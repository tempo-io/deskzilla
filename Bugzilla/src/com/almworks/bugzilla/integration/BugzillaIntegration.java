package com.almworks.bugzilla.integration;

import com.almworks.api.connector.*;
import com.almworks.api.connector.http.DocumentLoader;
import com.almworks.api.http.*;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.integration.err.*;
import com.almworks.bugzilla.integration.oper.*;
import com.almworks.util.*;
import com.almworks.util.commons.Procedure;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.progress.Progress;
import com.almworks.util.threads.Threads;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.*;
import org.almworks.util.detach.Lifespan;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * This is facade class for all operations on this bugzilla link. All operations are synchronized and
 * cannot happen simultaneously.
 *
 * @author sereda
 */
public class BugzillaIntegration implements AuthenticationMaster {
  private static final boolean EXCLUDE_ATTACHMENTS = Env.getBoolean("bugzilla.qload.noattach", false);
  private static final int LOAD_DICTIONARY_ATTEMPTS = 3;
  // https://bugzilla.mozilla.org/show_bug.cgi?id=632717
  private static final BugzillaVersion MAX_SEARCH_RESULTS_PARAM = BugzillaVersion.V4_2;
  // https://bugzilla.mozilla.org/show_bug.cgi?id=632718
  private static final BugzillaVersion DEFAULT_SEARCH_LIMIT = BugzillaVersion.V4_2;

  private final String myBaseURL;
  @Nullable
  private final TimeZone myDefaultTimezone;
  private final HttpMaterial myMaterial;
  @Nullable
  private final String myEmailSuffix;
  private final AtomicReference<String> myBzVersion;
  private final AtomicReference<ServerInfo> myInfo = new AtomicReference<ServerInfo>(null);

  private String myPassword = null;
  private String myUsername = null;
  private String myOverrideCharset = null;
  private Procedure<String> myAccountNameSink = null;
  private boolean myEnvAuthenticationDetected = false;

  private ConnectorStateStorage myStateStorage;
  private volatile Procedure<String> myVersionSink;

  private final QueryLoaderOperation myQueryLoader;
  
  public BugzillaIntegration(String baseURL, HttpClientProvider httpClientProvider, HttpLoaderFactory loaderFactory,
    @Nullable TimeZone defaultTimezone, @Nullable String userAgent, @Nullable String emailSuffix, @Nullable String bzVersion) throws MalformedURLException
  {
    myEmailSuffix = emailSuffix;
    myBzVersion = new AtomicReference<String>(bzVersion);
    myBaseURL = normalizeURL(baseURL);
    myDefaultTimezone = defaultTimezone;
    myMaterial = new DefaultHttpMaterial(httpClientProvider, loaderFactory);
    myMaterial.setUserAgent(userAgent);
    myQueryLoader = new QueryLoaderOperation(); 
  }

  public void setStateStorage(ConnectorStateStorage storage) {
    myStateStorage = storage;
  }

  public ConnectorStateStorage getStateStorage() {
    ConnectorStateStorage storage = myStateStorage;
    if (storage == null)
      storage = myStateStorage = new DefaultStateStorage();
    return storage;
  }

  public synchronized void close() {
    myMaterial.dispose();
  }

  public String getArtifactURL(Integer bugID) {
    String baseUrl = myBaseURL;
    return getBugUrl(baseUrl, bugID);
  }

  public static String getBugUrl(String baseUrl, Integer bugID) {
    return baseUrl + BugzillaHTMLConstants.URL_FRONT_PAGE + bugID;
  }

  public Integer getBugIdFromURL(String url) {
    if (url == null)
      return null;
    if (!url.startsWith(myBaseURL))
      return null;
    url = url.substring(myBaseURL.length());
    if (url.length() == 0)
      return null;
    if (url.charAt(0) == '/')
      url = url.substring(1);
    Matcher matcher = BugzillaHTMLConstants.URL_ID_EXRACTOR.matcher(url);
    if (!matcher.matches())
      return null;
    String idString = matcher.group(1);
    try {
      return Integer.parseInt(idString);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public synchronized BugzillaLists getBugzillaLists(@Nullable BugzillaRDFConfig rdfConfig, @Nullable List<String> productsList) throws ConnectorException {
    try {
      return getBugzillaListsFromUrl(myBaseURL + BugzillaHTMLConstants.URL_QUERY_FOR_LOAD_DICTIONARIES, rdfConfig, productsList);
    } catch (ConnectorException e) {
      if (e instanceof CancelledException)
        throw e;
      Log.warn(e);
      return getBugzillaListsFromUrl(myBaseURL + BugzillaHTMLConstants.URL_QUERY_PAGE_NOFORMAT, rdfConfig, productsList);
    }
  }

  /**
   * @param productsList limit by product list - when null, no limit
   */
  @Nullable
  public BugzillaRDFConfig loadRDFConfig(@Nullable List<String> productsList) throws ConnectorException {
    try {
      BugzillaRDFConfig config = new RDFConfigLoader(getMaterial(), myBaseURL, productsList, this).loadRDFConfig();
      String version = config.getInstallVersion();
      myBzVersion.compareAndSet(null, version);
      ServerInfo serverInfo = myInfo.get();
      if (serverInfo != null && version != null) serverInfo.updateVersion(version);
      Procedure<String> versionSink = myVersionSink;
      if (version != null && versionSink != null) versionSink.invoke(version);
      return config;
    } catch (ConnectorException e) {
      Log.warn("cannot load rdf config", e);
      throw e;
    }
  }

  public void ensureVersionIsKnown() throws ConnectorException {
    if (myBzVersion.get() != null) return;
    loadRDFConfig(Collections.<String>emptyList());
  }

  public synchronized Map<String, BugzillaProductInformation> getProductsInformation(Collection<String> products)
    throws ConnectorException
  {
    if (!isAuthenticationAvailable())
      throw new BugzillaLoginRequiredException();
    LinkedHashMap<String, BugzillaProductInformation> result = Collections15.linkedHashMap();
    for (String product : products) {
      BugzillaProductInformation info = getProductInformation(product);
      if (info == null) {
        assert false : product;
        continue;
      }
      result.put(product, info);
    }
    return result;
  }

  public BugzillaProductInformation getProductInformation(String product) throws ConnectorException {
    if (!isAuthenticationAvailable())
      throw new BugzillaLoginRequiredException();
    ensureVersionIsKnown();
    ProductInformationLoader retriever = new ProductInformationLoader(getServerInfo(), product);
    return retriever.getInfo();
  }

  private BugzillaLists getBugzillaListsFromUrl(String url, @Nullable BugzillaRDFConfig rdfConfig, @Nullable List<String> productsList) throws ConnectorException {
    BugzillaLoginRequiredException lastException = null;
    if (rdfConfig == null) {
      rdfConfig = loadRDFConfig(productsList);
    }
    ensureVersionIsKnown();
    for (int i = 0; i < LOAD_DICTIONARY_ATTEMPTS; i++) {
      try {
        LoadDictionaries retriever = new LoadDictionaries(getMaterial(), url, isAuthenticationAvailable(), this, rdfConfig);
        return retriever.retrieveLists();
      } catch (BugzillaLoginRequiredException e) {
        Log.debug("retrying " + LoadDictionaries.class);
        lastException = e;
      }
    }
    assert lastException != null;
    throw lastException;
  }

  public QueryURLBuilder getURLQueryBuilder() {
    String timeZone = null;
    String bzVersion = myBzVersion.get();
    BugzillaVersion version = bzVersion != null ? BugzillaVersion.parse(bzVersion) : null;
    if (version == null) Log.warn("Missing bugzilla version " + bzVersion);
    else if (version.compareTo(BugzillaVersion.V4_2) >= 0) timeZone = "GMT-0800"; // Assume that 4.2 is server at most west location
    return new QueryURLBuilder(myOverrideCharset, timeZone);
  }

  public synchronized boolean loadBugDetails(Integer[] IDs, Progress progress, Procedure<BugInfo> sink)
    throws ConnectorException
  {
    ensureVersionIsKnown();
    LoadBugsXML loader = createLoader(IDs, progress);
    loader.loadBugsInto(sink);
    return false;
  }

  public synchronized Collection<BugInfo> loadBugDetails(Integer[] IDs, Progress progress) throws ConnectorException {
    ensureVersionIsKnown();
    LoadBugsXML loader = createLoader(IDs, progress);
    return loader.loadBugs();
  }

  private LoadBugsXML createLoader(Integer[] IDs, Progress progress) throws ConnectorException {
    return new LoadBugsXML(getServerInfo(), IDs, progress,
      EXCLUDE_ATTACHMENTS ? "excludefield=attachment" : "excludefield=attachmentdata", false,
      EXCLUDE_ATTACHMENTS);
  }

  private ServerInfo getServerInfo() throws ConnectorException {
    while (true) {
      ServerInfo serverInfo = myInfo.get();
      if (serverInfo != null) return serverInfo;
      myInfo.compareAndSet(null, new ServerInfo(getMaterial(), myBaseURL, getStateStorage(), myDefaultTimezone, this, myEmailSuffix, myBzVersion.get()));
    }
  }

  public synchronized Collection<BugInfo> loadBugDetailsOnlyIdAndMtime(Integer[] IDs, Progress progress)
    throws ConnectorException
  {
    ensureVersionIsKnown();
    LoadBugsXML loader = new LoadBugsXML(getServerInfo(), IDs, progress, "field=bug_id&field=delta_ts", false, true);
    return loader.loadBugs();
  }

  /**
   * @return map name->url
   */
  public synchronized List<Pair<String, String>> loadSavedSearches() throws ConnectorException {
    ensureVersionIsKnown();
    LoadSavedSearches loader = new LoadSavedSearches(getMaterial(), myBaseURL, this);
    return loader.loadSavedSearches();
  }

  /** Loads a potentially large query. */
  public synchronized List<BugInfoMinimal> loadQuery(QueryURL query, Progress progress) throws ConnectorException {
    ensureVersionIsKnown();
    ServerInfo serverInfo = getServerInfo();
    boolean usePaging = serverInfo.versionAtLeast(MAX_SEARCH_RESULTS_PARAM) && query instanceof QueryURL.Changeable;
    if (!usePaging) {
      return new LoadQuery(myMaterial, myBaseURL + query.getURL(), this).loadBugs(progress);      
    } else {
      boolean overrideSoftLimit = serverInfo.versionAtLeast(DEFAULT_SEARCH_LIMIT);
      QueryPaging loader = new QueryPaging(myQueryLoader, (QueryURL.Changeable) query, overrideSoftLimit);
      return loader.loadBugs(progress);
    }
  }
  
  /** Loads a query known to be small or query from which only a small portion of first bugs is used. 
   * "Small" means less than any sensible max_search_results Bugzilla parameter (default is 10000.) */
  public synchronized List<BugInfoMinimal> loadSmallQuery(String queryURL, Progress progress) throws ConnectorException {
    ensureVersionIsKnown();
    LoadQuery loader = new LoadQuery(getMaterial(), myBaseURL + queryURL, this);
    return loader.loadBugs(progress);
  }

  public int countBugs(String queryURL, @Nullable Progress progress) throws ConnectorException {
    ensureVersionIsKnown();
    CountQuery counter = new CountQuery(getMaterial(), myBaseURL + queryURL, this);
    int result = counter.count();
    Log.debug("BI: bug count " + result);
    if (result >= 0) {
      if (progress != null)
        progress.setDone();
      return result;
    }
    LoadQuery loader = new LoadQuery(getMaterial(), myBaseURL + queryURL, this);
    int loadedResult = loader.loadBugs(progress).size();
    Log.debug("BI: bug count (loaded query) " + loadedResult);
    return loadedResult;
  }

  public Map<String, Integer> loadProductBugCounts() throws ConnectorException {
    ensureVersionIsKnown();
    final BugsByProductCountQuery query = new BugsByProductCountQuery(getMaterial(), myBaseURL, this);
    return query.countBugs();
  }

  public synchronized BugSubmitResult submitBug(BugInfoForUpload bugInfo) throws ConnectorException {
    checkWriteAccess();
    ensureVersionIsKnown();
    SubmitBug submitter =
      new SubmitBug(getServerInfo(), this, myBaseURL + BugzillaHTMLConstants.URL_SUBMIT_BUG, myUsername);
    return submitter.submit(bugInfo);
  }

  private void checkWriteAccess() throws BugzillaAccessException {
    if (!isAuthenticationAvailable())
      throw new BugzillaAccessException("no credentials", L.tooltip("No credentials"), L.tooltip(
        "You have to specify your username\n and a password to be able to submit \nor change bugs in Bugzilla."));
  }

  /**
   * Returns number of update requests performed.
   */
  public synchronized int updateBug(BugInfoForUpload bugInfo) throws ConnectorException {
    checkWriteAccess();
    String id = bugInfo.getAnyValue(BugzillaAttribute.ID, null);
    if (id == null)
      throw new UploadException("cannot update bug without id", L.tooltip("Cannot upload bug without bug ID"),
        L.tooltip("Cannot upload bug without ID. \n\nThe bug does not have ID set. This is probably a defect in " +
          "the application, please send a word to support team."));
    ensureVersionIsKnown();
    UpdateBug updater = new UpdateBug(getServerInfo(), id, bugInfo, myUsername);
    return updater.update(true);
  }


  public synchronized List<Pair<BugzillaUser, Integer>> loadVotes(Integer ID) throws ConnectorException {
    ensureVersionIsKnown();
    LoadVotes loader = new LoadVotes(getServerInfo(), String.valueOf(ID));
    List<Pair<BugzillaUser, Integer>> r = loader.loadBugVotes();
    Log.debug(this + ": loaded votes for [" + ID + "]: " + r);
    return r;
  }

  public synchronized UserVoteInfo loadVotesDefaults() throws ConnectorException {
    if (!isAuthenticationAvailable())
      return null;
    ensureVersionIsKnown();
    LoadVotes loader = new LoadVotes(getServerInfo(), null);
    UserVoteInfo r = loader.loadMyVotes();
    Log.debug(this + ": loaded user votes: " + r);
    return r;
  }

  public synchronized List<ChangeSet> loadActivity(Integer ID) throws ConnectorException {
    ensureVersionIsKnown();
    LoadActivity loader = new LoadActivity(getServerInfo(), ID);
    return loader.loadActivity();
  }

  public synchronized FrontPageData loadBugPage(Integer ID) throws ConnectorException {
    ensureVersionIsKnown();
    assert ID != null;
    LoadFrontPage loader = new LoadFrontPage(getServerInfo(), getBugUrl(myBaseURL, ID), ID);
    return loader.loadFrontPage();
  }

  /**
   * @see com.almworks.bugzilla.integration.oper.LoadRequestPage#loadRequestPage()  @param singleProject
   */
  @Nullable
  public Pair<Pair<List<String>, List<Integer>>, Pair<List<String>, List<Integer>>> loadRequestPage(
    @Nullable String singleProject) throws ConnectorException
  {
    ensureVersionIsKnown();
    return new LoadRequestPage(getMaterial(), this, myBaseURL, singleProject).loadRequestPage();
  }

  /**
   * @return all flags and flag types available for current user to requested attachment
   */
  public List<FrontPageData.FlagInfo> loadAttachmentPage(Integer attachmentId) throws ConnectorException {
    ensureVersionIsKnown();
    if (attachmentId == null) return Collections.emptyList();
    return new LoadAttachmentPage(getServerInfo(), attachmentId).loadAttachmentPage();
  }

  public synchronized Map<String, String> loadKeywords() throws ConnectorException {
    ensureVersionIsKnown();
    LoadKeywords loader =
      new LoadKeywords(getMaterial(), myBaseURL + BugzillaHTMLConstants.URL_KEYWORDS_DESCRIPTION, this);
    return loader.loadKeywords();
  }

  public synchronized void setCancelFlag(Lifespan lifespan, ScalarModel<Boolean> cancelFlag) {
    myMaterial.setCancelFlag(lifespan, cancelFlag);
  }

  public synchronized void setCredentials(String username, String password, final BugzillaAccountNameSink accountNameSink) {
    myUsername = username;
    myPassword = password;
    myAccountNameSink = accountNameSink == null ? null :
      new Procedure<String>() {
        @Override
        public void invoke(String arg) {
          accountNameSink.updateAccountName(BugzillaUser.shortEmailName(arg, null, myEmailSuffix));
        }
      };
  }


  public synchronized void setFeedbackHandler(FeedbackHandler feedbackHandler) {
    myMaterial.setFeedbackHandler(feedbackHandler);
  }

  String getBaseURL() {
    return myBaseURL;
  }

  public synchronized void checkAuthentication() throws ConnectorException {
    Threads.assertLongOperationsAllowed();
    authenticate();
  }

  private void authenticate() throws ConnectorException {
    if (!isAuthenticationAvailable())
      return;
    if (isAuthenticated())
      return;
    String url = myBaseURL + BugzillaHTMLConstants.URL_AUTHENTICATE;
    Authenticate auth = new Authenticate(myMaterial, url, myUsername, myPassword, myAccountNameSink);
    BugzillaAuthType authType = auth.authenticate();
    if (authType == BugzillaAuthType.ENV) {
      myEnvAuthenticationDetected = true;
    }
    if (!isAuthenticated()) {
      throw new BugzillaAccessException("Bugzilla Authentication Failed", L.tooltip("Bugzilla Authentication Failed"),
        L.tooltip(
          "Bugzilla authentication failed. \n\n" + "Most probably the problem is incorrect Bugzilla username and " +
            "password. Please review your username and password, or " + "verify them using URL:\n\n" + myBaseURL +
            BugzillaHTMLConstants.URL_LOGIN_SCREEN));
    }
  }

  public void reauthenticate() throws ConnectorException {
    clearAuthentication();
    authenticate();
  }

  public void clearAuthentication() {
    HttpUtils.removeCookies(myMaterial.getHttpClient().getState(), BugzillaHTMLConstants.COOKIE_BUGZILLA_LOGIN,
      BugzillaHTMLConstants.COOKIE_BUGZILLA_LOGINCOOKIE);
    myEnvAuthenticationDetected = false;
  }

  private HttpMaterial getMaterial() throws ConnectorException {
    Threads.assertLongOperationsAllowed();
    myMaterial.setCharset(myOverrideCharset);
    authenticate();
    return myMaterial;
  }

  private boolean isAuthenticated() {
    if (myEnvAuthenticationDetected)
      return true;
    Cookie[] cookies = myMaterial.getHttpClient().getState().getCookies();
    int authCookieCount = 0;
    for (Cookie cookie : cookies) {
      String cookieName = cookie.getName();
      if (cookieName.equalsIgnoreCase(BugzillaHTMLConstants.COOKIE_BUGZILLA_LOGIN) ||
        cookieName.equalsIgnoreCase(BugzillaHTMLConstants.COOKIE_BUGZILLA_LOGINCOOKIE))
      {
        authCookieCount++;
      }
    }
    return authCookieCount >= 2;
  }

  public boolean isAuthenticationAvailable() {
    return myUsername != null && myPassword != null;
  }


  public static String normalizeURL(String urlString) throws MalformedURLException {
    if (urlString == null)
      throw new MalformedURLException("null url");
    urlString = urlString.trim();
    if (urlString.length() == 0)
      throw new MalformedURLException("empty url");

    URL url = null;
    try {
      url = new URL(urlString);
    } catch (MalformedURLException e) {
      if (urlString.indexOf("://") < 0) {
        urlString = "http://" + urlString;
        url = new URL(urlString);
      } else {
        throw e;
      }
    }

    String path = url.getPath();
    if (path.endsWith("/"))
      path = path.substring(0, path.length() - 1);
    if (path.endsWith(".cgi")) {
      int k = path.lastIndexOf('/');
      if (k >= 0)
        path = path.substring(0, k);
    }
    url = new URL(url.getProtocol(), url.getHost(), url.getPort(), path);
    String result = url.toExternalForm();
    if (!result.endsWith("/"))
      result = result + "/";

    try {
      // do other checks that are not done by URL, for example - host is not empty check
      new GetMethod(result + BugzillaHTMLConstants.URL_QUERY_PAGE_ADVANCED);
    } catch (Exception e) {
      throw new MalformedURLException(e.toString());
    }

    return result;
  }

  public synchronized void checkConnection() throws ConnectorException {
    final String baseURL = getBaseURL();
    final String url = baseURL + BugzillaHTMLConstants.URL_AUTHENTICATE;

    class Checker extends BugzillaOperation implements RunnableRE<Void, ConnectorException> {
      Checker(HttpMaterial material, @Nullable AuthenticationMaster authenticationMaster) {
        super(material, authenticationMaster);
      }

      @Override
      public Void run() throws ConnectorException {
        DocumentLoader loader = getDocumentLoader(url, true, "check_connection");
        Document topPage = loader.httpGET().loadHTML();
        Element body = JDOMUtils.searchElement(topPage.getRootElement(), "body");
        if (body == null)
          throw new BugzillaResponseException("cannot load bugzilla top page [" + url + "]",
            L.tooltip("Bugzilla not found at " + baseURL), "");
        return null;
      }

      public void check() throws ConnectorException {
        runOperation(this);
      }
    }

    new Checker(getMaterial(), this).check();
  }

  public synchronized void setOverrideCharset(String charset) {
    myOverrideCharset = charset;
  }

  public synchronized long getLastServerResponseTime() {
    return myMaterial.getLastServerResponseTime();
  }

  public synchronized void clearLastServerResponseTime() {
    myMaterial.setLastServerResponseTime(0);
  }

  public static String getShowAttachmentURL(String baseURL, Integer id) {
    return getIdUrl(baseURL, id, BugzillaHTMLConstants.SHOW_ATTACHMENT_SCRIPT);
  }

  public String getDownloadAttachmentURL(Integer attachmentID) {
    return getIdUrl(getBaseURL(), attachmentID, BugzillaHTMLConstants.DOWNLOAD_ATTACHMENT_SCRIPT);
  }

  public static String getDownloadAttachmentURL(String baseUrl, Integer attachmentId) {
    return getIdUrl(baseUrl, attachmentId, BugzillaHTMLConstants.DOWNLOAD_ATTACHMENT_SCRIPT);
  }

  public static String getIdUrl(String baseURL, Integer id, String pageRegexp) {
    if (baseURL == null || id == null)
      return null;
    return baseURL + pageRegexp.replaceAll("\\$id\\$", id.toString());
  }

  public HttpLoader getAttachmentHttpLoader(int attachmentId) throws ConnectorException {
    HttpMaterial material = getMaterial();
    final String url = getDownloadAttachmentURL(attachmentId);
    return material.createLoader(url, new HttpMethodFactory() {
      public HttpMethodBase create() throws HttpMethodFactoryException {
        return HttpUtils.createGet(url);
      }
    });
  }

  public void validateAttachmentDownload(int attachmentId, String mimeType, File downloadedFile)
    throws ConnectorException
  {
    if (mimeType != null && !"text/html".equalsIgnoreCase(mimeType))
      return;
    Document document;
    try {
      document = new DocumentLoader(getMaterial(), downloadedFile).fileLoad().loadHTML();
    } catch (ConnectorException e) {
      // ignore exception - nothing to validate
      return;
    }
    BugzillaErrorDetector.detectAndThrow(document, "attachment download");
  }

  public void setNoProxy() {
    myMaterial.setIgnoreProxy(true);
  }

  private static final Pattern BASE_URL_EXRACTOR = Pattern.compile("^(https?\\:\\/\\/.*)\\/show_bug\\.cgi\\?id\\=(\\d+)$");
  public static String getBaseUrlFromArtifactUrl(String url) {
    if (url == null)
      return null;
    Matcher matcher = BASE_URL_EXRACTOR.matcher(url);
    if (matcher.matches())
      return matcher.group(1);
    else
      return null;
  }
  
  public static Integer getBugIdFromUrl(String url) {
    if (url == null) return null;
    Matcher m = BASE_URL_EXRACTOR.matcher(url);
    if (!m.matches()) return null;
    try {
      return Integer.parseInt(m.group(2));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public String toString() {
    return "BI[" + myBaseURL + "]";
  }

  public static boolean isVersionOrLater(String version, String test) {
    if (test == null || version == null)
      return false;
    String[] ve = version.split("\\.");
    String[] te = test.split("\\.");
    int len = Math.min(te.length, ve.length);
    for (int i = 0; i < len; i++) {
      int v = Util.toInt(ve[i], -1);
      int t = Util.toInt(te[i], -1);
      if (t < 0 || v < 0)
        return false;
      if (v != t)
        return v > t;
    }
    return te.length <= ve.length;
  }

  public void setVersionSink(Procedure<String> versionSink) {
    myVersionSink = versionSink;
  }
  
  private class QueryLoaderOperation implements QueryLoader {
    @NotNull
    @Override
    public List<BugInfoMinimal> load(@NotNull String urlSuffix, @Nullable Progress progress) throws ConnectorException {
      return new LoadQuery(myMaterial, myBaseURL + urlSuffix, BugzillaIntegration.this).loadBugs(progress);
    }
  }
}

