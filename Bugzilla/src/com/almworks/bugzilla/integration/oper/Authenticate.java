package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.*;
import com.almworks.api.http.HttpMaterial;
import com.almworks.bugzilla.integration.BugzillaErrorDetector;
import com.almworks.bugzilla.integration.BugzillaHTMLConstants;
import com.almworks.bugzilla.integration.err.BugzillaAccessException;
import com.almworks.util.*;
import com.almworks.util.commons.Condition;
import com.almworks.util.commons.Procedure;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.*;
import org.apache.commons.httpclient.NameValuePair;
import org.jdom.*;

import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Authenticate extends BugzillaOperation {
  private final String myUsername;
  private final String myPassword;
  private final String myUrl;
  private final Procedure<String> myUpdateLogin;
  private static final Pattern LOGGED_IN_AS_PATTERN = Pattern.compile(".*logged(?:\\s|&nbsp;)+in(?:\\s|&nbsp;)+as(?:\\s|&nbsp;)+([^\\s\\|\\<\\(]+).*", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

  public Authenticate(HttpMaterial material, String url, String username, String password, Procedure<String> updateLogin)
  {
    super(material, null);
    myUrl = url;
    myUsername = username;
    myPassword = password;
    myUpdateLogin = updateLogin;
  }

  public BugzillaAuthType authenticate() throws ConnectorException {
    loadLoginPage();
    Document page = loadLoginPage();
    List<Pair<String, String>> loginParams = buildLoginParams(page);
    if (loginParams == null)
      return BugzillaAuthType.ENV;
    page = submitCredentials(loginParams);
    notifyBugzillaLogin(page);
    return BugzillaAuthType.CGI;
  }

  List<Pair<String, String>> buildLoginParams(Document loginPage) {
    List<Pair<String, String>> loginParams = null;
    if (loginPage != null) {
      loginParams = findLoginForm(loginPage.getRootElement());
    }
    if (loginParams == null) {
      if (loginPage != null && checkLoggedInWithEnv(loginPage.getRootElement())) {
        return null;
      }
      loginParams = createDefaultLoginParams();
    }
    assert loginParams != null;
    loginParams = Collections15.arrayList(loginParams);
    updateWithCredentials(loginParams, myUsername, myPassword);
    return loginParams;
  }

  private boolean checkLoggedInWithEnv(Element root) {
    if (
      // <div class="links"> probably works with ancient Bugzillas like 2.14, but I have never seen it work
      findLoggedInAs(JDOMUtils.searchElementIterator(root, "div", "class", "links"))
      // Works with Bugzilla 3.0 and above
      || findLoggedInAs(JDOMUtils.multiSearchElements(root, JDOMUtils.byTagAndAttr("ul", "class", "links"), JDOMUtils.byTag("li")).iterator()))
    {
      Log.debug("Env login detected");
      return true;
    }
    return false;
  }

  private boolean findLoggedInAs(Iterator<Element> ii) {
    while (ii.hasNext()) {
      Matcher matcher = LOGGED_IN_AS_PATTERN.matcher(JDOMUtils.getTextTrim(ii.next()));
      if (matcher.matches()) {
        String loginName = matcher.group(1);
        if (myUpdateLogin != null && loginName.length() > 0) {
          myUpdateLogin.invoke(loginName);
        }
        return true;
      }
    }
    return false;
  }

  private void notifyBugzillaLogin(Document page) throws BugzillaAccessException {
    if (myUpdateLogin == null)
      return;
    if (page == null || !page.hasRootElement()) {
      LogHelper.warning("Missing login response");
      throw new BugzillaAccessException("Bugzilla Authentication Failed", L.tooltip("Bugzilla Authentication Failed"),
        L.tooltip(
          "Bugzilla authentication failed. \n\n" + "Most probably the problem is incorrect Bugzilla username and " +
            "password. Please review your username and password, or " + "verify them using URL:\n\n" + myUrl));
    }
    Element root = page.getRootElement();
    List<Element> element = JDOMUtils.searchElements(root, "a");
    for (Element a : element) {
      String href = JDOMUtils.getAttributeValue(a, "href", "", true);
      if (!href.equalsIgnoreCase(BugzillaHTMLConstants.URL_RELOGIN) && !href.equalsIgnoreCase(BugzillaHTMLConstants.URL_RELOGIN_2))
        continue;
      String action = JDOMUtils.getTextTrim(a);
      String despacified = action.replaceAll("\\s+", "");
      if (!"logout".equalsIgnoreCase(despacified))
        continue;
      Element parent = a.getParentElement();
      int k = parent.indexOf(a);
      assert k >= 0;
      if (k < 0)
        continue;
      Element temp = new Element("temp");
      for (k++; k < parent.getContentSize(); k++) {
        Content content = (Content) parent.getContent(k).clone();
        temp.addContent(content);
      }
      String text = JDOMUtils.getTextTrim(temp);
      if (text.length() == 0)
        continue;
      int space = text.indexOf(' ', 1);
      if (space >= 0)
        text = text.substring(0, space);
      if (text.length() > 0 && isOverridingBugzillaLogin(text)) {
        myUpdateLogin.invoke(text);
        return;
      }
    }
    Log.warn("cannot find log out marker on " + myUrl);
  }

  private boolean isOverridingBugzillaLogin(String value) {
    if (value == null)
      return false;
    value = value.trim();
    if (value.length() == 0)
      return false;
    // check if Bugzilla_login is a number (user id)
    try {
      Integer.parseInt(value);
      return false;
    } catch (NumberFormatException e) {
      return true;
    }
  }

  private static List<Pair<String, String>> createDefaultLoginParams() {
    List<Pair> params = Collections15.arrayList(new Pair[] {
      Pair.create("Bugzilla_login", ""), Pair.create("Bugzilla_password", ""), Pair.create("GoAheadAndLogIn", "1"),
      Pair.create("GoAheadAndLogIn", "Login"),});
    return (List) params;
  }

  static List<Pair<String, String>> findLoginForm(Element page) {
    HtmlForm form = HtmlUtils.findForm(page, new Condition<HtmlForm>() {
      public boolean isAccepted(HtmlForm form) {
        if (form.getMethod() != HtmlForm.Method.POST)
          return false;
        List<Pair<String, String>> parameters = form.getRequiredFormParameters();
        boolean hasUsername = false;
        boolean hasPassword = false;
        for (Pair<String, String> pair : parameters) {
          String name = Util.upper(pair.getFirst());
          if (name.endsWith("_LOGIN"))
            hasUsername = true;
          else if (name.endsWith("_PASSWORD"))
            hasPassword = true;
          if (hasPassword && hasUsername)
            return true;
        }
        return false;
      }
    });
    if (form == null) {
      Log.warn("cannot find login form, assuming default login parameters");
      return null;
    } else {
      return form.getRequiredFormParameters();
    }
  }

  private Document submitCredentials(final List<Pair<String, String>> parameters) throws ConnectorException {
    return runOperation(new RunnableRE<Document, ConnectorException>() {
      public Document run() throws ConnectorException {
        List<NameValuePair> post = HtmlUtils.PAIR_TO_NVP.collectList(parameters);
        try {
          DocumentLoader loader = getDocumentLoader(myUrl, true);
          loader.setScriptOverride("auth");
          Document document = loader.httpPOST(post).loadHTML();
          BugzillaErrorDetector.detectAndThrow(document, "authentication2");
          return document;
        } catch (IllegalArgumentException e) {
          throw new ConnectionException(myUrl, "authentication error - internal", e);
        }
      }
    });
  }

  private Document loadLoginPage() {
    try {
      return runOperation(new RunnableRE<Document, ConnectorException>() {
        public Document run() throws ConnectorException {
          String url = myUrl + BugzillaHTMLConstants.GO_AHEAD_AND_LOGIN;
          Document document = getDocumentLoader(url, true, "auth-page").httpGET().loadHTML();
          BugzillaErrorDetector.detectAndThrow(document, "authentication1", true);
          return document;
        }
      });
    } catch (ConnectorException e) {
      Log.warn("cannot load login page, assuming default login parameters", e);
      return null;
    }
  }

  static void updateWithCredentials(List<Pair<String, String>> parameters, String username, String password) {
    boolean hadUsername = false;
    boolean hadPassword = false;
    List<Pair<String, String>> replaced = Collections15.arrayList(2);
    for (Iterator<Pair<String, String>> ii = parameters.iterator(); ii.hasNext();) {
      Pair<String, String> pair = ii.next();
      String name = pair.getFirst();
      String nameUpper = Util.upper(name);
      if (nameUpper.endsWith("_LOGIN")) {
        replaced.add(Pair.create(name, username));
        ii.remove();
        hadUsername = true;
      } else if (nameUpper.endsWith("_PASSWORD")) {
        replaced.add(Pair.create(name, password));
        ii.remove();
        hadPassword = true;
      }
    }
    parameters.addAll(replaced);
    if (!hadUsername || !hadPassword) {
      assert false : parameters;
      Log.warn("couldn't find username or password in params");
    }
  }
}
