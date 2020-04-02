package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.*;
import com.almworks.api.http.*;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.data.BugInfoMinimal;
import com.almworks.bugzilla.integration.data.PLoadQuery;
import com.almworks.bugzilla.integration.err.BugzillaResponseException;
import com.almworks.util.RunnableRE;
import com.almworks.util.io.StringTransferTracker;
import com.almworks.util.progress.Progress;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.*;
import org.apache.commons.httpclient.*;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.filter.ElementFilter;
import org.jetbrains.annotations.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author sereda
 */
public class LoadQuery extends BugzillaOperation {
  private static final Pattern SEARCH_HREF = Pattern.compile("show_bug\\.cgi.+id=(\\d+)", Pattern.CASE_INSENSITIVE);

  private final String myQueryURL;

  public static final RedirectURIHandler CTYPE_REDIRECT_VERIFIER = new RedirectURIHandler() {
    @Nullable
    public URI approveRedirect(URI initialUri, URI redirectUri) throws HttpLoaderException {
      if (redirectUri == null)
        return null;
      try {
        String uri = redirectUri.getEscapedURI();
        String changed = HttpUtils.addGetParameterIfMissing(uri, "ctype", "csv");
        if (changed == uri) {
          return redirectUri;
        } else {
          return new URI(changed, true);
        }
      } catch (URIException e) {
        Log.warn(e);
        return null;
      }
    }
  };

  public LoadQuery(HttpMaterial material, String queryURL, AuthenticationMaster authMaster) {
    super(material, authMaster);
    assert queryURL != null;
    myQueryURL = queryURL.trim();
  }

  // todo: check access
  public List<BugInfoMinimal> loadBugs(@Nullable final Progress progress) throws ConnectorException {
    return runOperation(new RunnableRE<List<BugInfoMinimal>, ConnectorException>() {
      public List<BugInfoMinimal> run() throws ConnectorException {
        setColumnsCookies(true);

        try {
          QueryPageTracker sink = progress == null ? null : new QueryPageTracker(progress);
          String url = addCsvCtype(myQueryURL);
          Log.debug("loading " + url);
          DocumentLoader loader = getDocumentLoader(url, true);
          loader.setTransferTracker(sink);
          loader.addRedirectUriHandler(CTYPE_REDIRECT_VERIFIER);
          boolean csv = true;
          try {
            loader.httpGET();
          } catch (HttpFailureConnectionException e) {
            // safety measure -- what if csv is not supported
            int statusCode = e.getStatusCode();
            if (statusCode / 100 == 5) {
              csv = false;
              Log.warn("looks like csv is not supported on server", e);
              Log.debug("loading " + myQueryURL);
              loader = getDocumentLoader(myQueryURL, true);
              loader.setTransferTracker(sink);
              loader.httpGET();
            } else {
              throw e;
            }
          }

          List<BugInfoMinimal> result = null;
          if (csv) {
            String csvString = loader.loadString();
            if (csvString != null)
              result = tryLoadFromCsv(csvString);
          }
          if (result == null) {
            Document document = loader.loadHTML();
            checkForBugzillaError(document);
            result = tryLoadFromCssTable(document);
            if (result == null) {
              result = tryLoadFromSimpleTable(document);
            }
          }
          return result != null ? result : Collections15.<BugInfoMinimal>emptyList();
        } catch (HttpCancelledException e) {
          throw new CancelledException(e);
        } finally {
          try {
            if (progress != null)
              progress.setDone();
          } catch (Exception e) {
            // ignore
          }
        }
      }
    });
  }

  static List<BugInfoMinimal> tryLoadFromCsv(String csvString) {
    final List<BugInfoMinimal> result = Collections15.arrayList();
    BugCSVParser parser = new BugCSVParser(result);
    boolean parsed = new CSVTokenizer(csvString).parse(parser);
    if (!parsed && csvString.length() > 10) {
      parser.clear();
      parsed = new CSVTokenizer(csvString, true).parse(parser);
    }
    if (!parsed) {
      Log.debug("cannot find csv in [" + StringUtil.limitString(csvString, 100) + "]");
    }
    return parsed ? result : null;
  }

  public static String addCsvCtype(String queryURL) {
    char delim = queryURL.indexOf('?') >= 0 ? '&' : '?';
    return queryURL + delim + "ctype=csv";
  }

  private static void checkForBugzillaError(Document document) throws ConnectorException {
    List<Element> elements = JDOMUtils.searchElements(document.getRootElement(), "h1");
    if (elements.size() != 1)
      return;
    String message = JDOMUtils.getTextTrim(elements.get(0)).replaceAll(":", "");
    List<Element> elementsPre = JDOMUtils.searchElements(document.getRootElement(), "pre");
    if (elementsPre.size() == 1) {
      String description = JDOMUtils.getTextTrim(elementsPre.get(0));
      throw new BugzillaResponseException(message, description, description);
    }
  }

  private static String findBugIDInSimpleTableRow(Element row) {
    Iterator<Element> ii = JDOMUtils.getChildren(row, "td").iterator();
    while (ii.hasNext()) {
      Element td = ii.next();
      Element anchor = JDOMUtils.getChild(td, "a");
      if (anchor == null)
        continue;
      String href = JDOMUtils.getAttributeValue(anchor, "href", null, true);
      if (href == null)
        continue;
      Matcher matcher = SEARCH_HREF.matcher(href);
      if (!matcher.matches())
        continue;
      String hrefID = matcher.group(1);
      String textID = JDOMUtils.getTextTrim(anchor);
      if (!hrefID.equals(textID))
        continue;
      return hrefID;
    }
    return null;
  }

  private static String findMTimeInSimpleTableRow(Element row, String id) {
    StringBuffer diagnosis = new StringBuffer();
    List<Element> cells = JDOMUtils.getChildren(row, "TD");
    // try to access anything that looks like a date
    for (Iterator<Element> icell = cells.iterator(); icell.hasNext();) {
      Element cell = icell.next();
      Iterator<Element> ichild = cell.getChildren().iterator();
      if (ichild.hasNext()) {
        Element e = ichild.next();
        if (ichild.hasNext())
          continue;
        if (!e.getName().equalsIgnoreCase("NOBR"))
          continue;
        cell = e;
      }

      String text = JDOMUtils.getTextTrim(cell);
      Date mtime = BugzillaDateUtil.parse(text, null);
      if (mtime != null) {
        return text;
      } else {
        diagnosis.append('(').append(text).append(')');
      }
    }
    Log.warn("cannot find mtime for bug [" + id + "][" + diagnosis.toString() + "]");
    return null;
  }

  private List<String> getCssBugTableColumnIDs(Element bugTable) throws CannotParseException {
    ArrayList<String> columns = new ArrayList<String>();
    Element colGroup = JDOMUtils.searchElement(bugTable, "colgroup");
    if (colGroup == null)
      throw new CannotParseException(myQueryURL, "cannot find colgroup in bugs table");
    List cols = colGroup.getContent(new ElementFilter("COL"));
    for (int i = 0; i < cols.size(); i++) {
      Element e = (Element) cols.get(i);
      String colClass = JDOMUtils.getAttributeValue(e, "class", null, false);
      assert colClass != null : myQueryURL;
      if (colClass == null)
        colClass = "?" + i;
      columns.add(colClass);
    }
    return columns;
  }

  private void setColumnsCookies(boolean set) throws ConnectorException {
    try {
      URL url = new URL(myQueryURL);
      String host = url.getHost();
      String path = url.getPath();
      path = HttpUtils.adjustPathForCookie(path);
      Cookie columnListCookie = new Cookie(host, "COLUMNLIST", "changeddate", path,
        set ? HttpUtils.COOKIE_NOT_EXPIRED : HttpUtils.COOKIE_EXPIRED, false);
      Cookie splitHeaderCookie = new Cookie(host, "SPLITHEADER", "0", path,
        set ? HttpUtils.COOKIE_NOT_EXPIRED : HttpUtils.COOKIE_EXPIRED, false);
      columnListCookie.setPathAttributeSpecified(true);
      splitHeaderCookie.setPathAttributeSpecified(true);
      HttpState state = myMaterial.getHttpClient().getState();
      state.addCookie(columnListCookie);
      state.addCookie(splitHeaderCookie);
    } catch (MalformedURLException e) {
      throw new ConnectorException("bad url", e, "Bad URL",
        "The URL is invalid, please check your configuration.\n" + myQueryURL);
    }
  }

  private List<BugInfoMinimal> tryLoadFromCssTable(Document document)
    throws ConnectorException, HttpCancelledException
  {
    myMaterial.checkCancelled();
    List<Element> bugTables = searchCssBugTable(document.getRootElement());
    if (bugTables.size() == 0) {
      return null;
    }
    List<BugInfoMinimal> result = Collections15.arrayList();
    for (Iterator<Element> iterator = bugTables.iterator(); iterator.hasNext();) {
      myMaterial.checkCancelled();
      Element bugTable = iterator.next();
      List<String> bugTableColumnsCssIDs = getCssBugTableColumnIDs(bugTable);
      Collection<Element> bugRows = getCssBugTableRows(bugTable);
      for (Iterator<Element> it = bugRows.iterator(); it.hasNext();) {
        myMaterial.checkCancelled();
        Map<String, String> map = loadBugFromCssTableRow(it.next(), bugTableColumnsCssIDs);
        String bugID = null;
        String bugModificationTime = null;
        for (Iterator<String> cssIterator = map.keySet().iterator(); cssIterator.hasNext();) {
          String cssClass = cssIterator.next();
          BugzillaAttribute bzAttribute = BugzillaHTMLConstants.CSS_CLASS_TO_ATTRIBUTE_MAP.get(cssClass);
          if (bzAttribute != null) {
            if (bzAttribute == BugzillaAttribute.ID)
              bugID = map.get(cssClass);
            else if (bzAttribute == BugzillaAttribute.MODIFICATION_TIMESTAMP)
              bugModificationTime = map.get(cssClass);
          } else {
            Log.warn("unknown css class id " + cssClass + " - value [" + map.get(cssClass) + "]");
          }
        }
        // try to guess time/id
        if (bugModificationTime == null && bugID != null) {
          for (Iterator<String> cssIterator = map.keySet().iterator(); cssIterator.hasNext();) {
            String cssClass = cssIterator.next();
            BugzillaAttribute bzAttribute = BugzillaHTMLConstants.CSS_CLASS_TO_ATTRIBUTE_MAP.get(cssClass);
            if (bzAttribute == null) {
              String v = map.get(cssClass);
              Date date = BugzillaDateUtil.parse(v, null);
              if (date != null) {
                bugModificationTime = v;
                break;
              }
            }
          }
        }
        if (bugID == null) {
          Log.warn("no id in bug row");
          continue;
        }
        try {
          Integer.parseInt(bugID);
        } catch (NumberFormatException e) {
          Log.warn("bad bug id " + bugID);
          continue;
        }
        if (bugModificationTime == null) {
          Log.warn("no mtime in bug row");
          continue;
        }
        BugInfoMinimal data = new BugInfoMinimal(bugID, bugModificationTime);
        result.add(data);
      }
    }
    return result;
  }

  private List<BugInfoMinimal> tryLoadFromSimpleTable(Document document) throws HttpCancelledException {
    // access all tables
    myMaterial.checkCancelled();
    List<BugInfoMinimal> result = Collections15.arrayList();
    List<Element> tables = JDOMUtils.searchElements(document.getRootElement(), "TABLE");
    for (Iterator<Element> iterator = tables.iterator(); iterator.hasNext();) {
      myMaterial.checkCancelled();
      Element table = iterator.next();
      List<Element> rows = JDOMUtils.searchElements(table, "TR");
      for (Iterator<Element> irow = rows.iterator(); irow.hasNext();) {
        myMaterial.checkCancelled();
        Element row = irow.next();
        // For each row, try to find TD with pointer to a bug.
        // Pointer to a bug has the form <A href=".*show_bug.cgi?id=NNN">NNN</A>
        // If pointer is found, then it is a bug row.
        String foundID = findBugIDInSimpleTableRow(row);
        if (foundID == null)
          continue;
        String foundMTime = findMTimeInSimpleTableRow(row, foundID);
        if (foundMTime == null) {
          continue;
        }
        result.add(new BugInfoMinimal(foundID, foundMTime));
      }
    }
    return result;
  }

  private static List<Element> getCssBugTableRows(Element bugTable) {
    ArrayList<Element> r = new ArrayList<Element>();
    Iterator<Element> ii = JDOMUtils.searchElementIterator(bugTable, "tr");
    while (ii.hasNext()) {
      Element e = ii.next();
      if (isBugRow(e))
        r.add(e);
    }
    return r;
  }

  private static boolean isBugRow(Element e) {
    return e.getContent(new ElementFilter("TD")).iterator().hasNext();
  }

  private static Map<String, String> loadBugFromCssTableRow(Element element, List<String> columnCssIDs) {
    HashMap result = new HashMap();
    Iterator cells = element.getContent(new ElementFilter("TD")).iterator();
    Iterator struc = columnCssIDs.iterator();
    while (cells.hasNext() && struc.hasNext()) {
      String cellClass = (String) struc.next();
      Element cell = (Element) cells.next();
      String value = null;
      if (cellClass.equals(BugzillaHTMLConstants.ID_COLUMN_CSS_CLASS)) {
        Iterator hrefit = cell.getContent(new ElementFilter("A")).iterator();
        Element href = hrefit.hasNext() ? (Element) hrefit.next() : null;
        if (href != null)
          value = JDOMUtils.getTextTrim(href);
      }
      if (value == null) {
        value = JDOMUtils.getTextTrim(cell);
      }
      result.put(cellClass, value);
    }
    return result;
  }

  private static List<Element> searchCssBugTable(Element root) throws CannotParseException {
    return JDOMUtils.searchElements(root, "table", "class", BugzillaHTMLConstants.BUGS_TABLE_CSS_CLASS);
  }

  private static class QueryPageTracker implements StringTransferTracker {
    private static final int MAX_BUGSFOUND_SEARCH_SIZE = 10000;

    private final Progress myProgress;
    private char[] myCountBuf = null;
    private long myTotalLength;

    public QueryPageTracker(Progress progress) {
      myProgress = progress;
    }

    public void setContentLengthHint(long length) {
      myTotalLength = length;
    }

    public void onTransfer(StringBuilder buffer) {
      boolean csv = buffer.length() > 6 && "bug_id".equals(buffer.substring(0, 6));
      if (csv) {
        int lines = 0;
        int k = -1;
        while ((k = buffer.indexOf("\n", k + 1)) >= 0)
          lines++;
        double progress = myTotalLength > 0 ? ((double) buffer.length()) / myTotalLength : 0.001 * lines;
        myProgress.setProgress(progress,
          PLoadQuery.bugsDownloadedPercent(Math.max(0, lines - 1), (int) Math.round(100 * progress)));
      } else {
        processOldProgress(buffer);
      }
    }

    private void processOldProgress(StringBuilder buffer) {
      int fullLength = buffer.length();
      int count = 0;
      CharSequence searchSequence = buffer;
      if (fullLength > MAX_BUGSFOUND_SEARCH_SIZE) {
        searchSequence = searchSequence.subSequence(0, MAX_BUGSFOUND_SEARCH_SIZE);
      }
      Matcher matcher = BugzillaHTMLConstants.BUGS_FOUND.matcher(searchSequence);
      if (matcher.matches()) {
        String s = matcher.group(1);
        try {
          count = Integer.parseInt(s);
        } catch (NumberFormatException e) {
          // ignore
        }
      }

      if (count <= 0) {
        // no bug count is available.
        // at maximum , suppose that no reasonable query returns more than 100MB of data.
        int length = fullLength;
        int mbytes = length / 1000000;
        if (mbytes > 0) {
          myProgress.setProgress(0.01F * mbytes, PLoadQuery.bytesDownloaded(length));
        }
      } else {
        float increment = 1F / count;
        int bugs = 0;
        int length = Math.min(MAX_BUGSFOUND_SEARCH_SIZE, fullLength);
        if (myCountBuf == null) {
          myCountBuf = new char[MAX_BUGSFOUND_SEARCH_SIZE];
        }
        buffer.getChars(0, length, myCountBuf, 0);
        String sample = BugzillaHTMLConstants.BUG_MARKER;
        int sampleLength = sample.length();
        char[] sampleChars = sample.toCharArray();
        char first = sampleChars[0];
        for (int i = 0; i < length - sampleLength; i++) {
          char c = myCountBuf[i];
          if (c == first) {
            int p = 1;
            int j = i + 1;
            for (; p < sampleLength; p++, j++) {
              if (myCountBuf[j] != sampleChars[p]) {
                break;
              }
            }
            if (p == sampleLength) {
              bugs++;
              i = j;
            }
          }
        }
        if (bugs > 0 && length < fullLength && length > 0) {
          bugs = (int) Math.round(((double) bugs) / length * fullLength);
        }

        float progress = Math.min(increment * bugs, 1F);
        myProgress.setProgress(progress, PLoadQuery.bugsDownloaded(bugs, count));
      }
    }
  }


  private static class BugCSVParser implements CSVTokenizer.CSVVisitor {
    private boolean myFirstLine;
    private int myBugIdColumn;
    private int myMtimeColumn;

    private String myCurrentId;
    private String myCurrentMtime;
    private final List<BugInfoMinimal> myResult;

    public BugCSVParser(List<BugInfoMinimal> result) {
      myResult = result;
      clear();
    }

    public boolean visitCell(int row, int col, String cell) {
      if (myFirstLine) {
        if ("bug_id".equals(cell)) {
          assert myBugIdColumn < 0 : myBugIdColumn + " " + col;
          myBugIdColumn = col;
        } else if ("changeddate".equals(cell) || "delta_ts".equals(cell)) {
          assert myMtimeColumn < 0 : myMtimeColumn + " " + col;
          myMtimeColumn = col;
        }
      } else {
        if (col == myBugIdColumn)
          myCurrentId = cell.trim();
        else if (col == myMtimeColumn)
          myCurrentMtime = cell.trim();
      }
      return true;
    }

    public boolean visitRow(int row) {
      if (myFirstLine) {
        if (myBugIdColumn < 0 || myMtimeColumn < 0)
          return false;
        myFirstLine = false;
      } else {
        if (myCurrentId != null && myCurrentMtime != null)
          myResult.add(new BugInfoMinimal(myCurrentId, myCurrentMtime));
        myCurrentId = myCurrentMtime = null;
      }
      return true;
    }

    public final void clear() {
      myResult.clear();
      myFirstLine = true;
      myBugIdColumn = -1;
      myMtimeColumn = -1;
      myCurrentId = null;
      myCurrentMtime = null;
    }
  }
}
