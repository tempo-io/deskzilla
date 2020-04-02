package com.almworks.api.connector.http;

import com.almworks.api.http.*;
import com.almworks.util.*;
import com.almworks.util.io.IOUtils;
import org.almworks.util.*;
import org.apache.commons.httpclient.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class HttpDumper implements HttpReportAcceptor {
  private static final SimpleDateFormat DAY_DIR_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
  private static final SimpleDateFormat FILENAME_FORMAT = new SimpleDateFormat("DDD-HHmmss-SSS");
  private static final SimpleDateFormat TIMESTAMP_IN_FILE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

  private static final Comparator<NameValuePair> NVP_COMPARATOR = new Comparator<NameValuePair>() {
    public int compare(NameValuePair o1, NameValuePair o2) {
      return o1.getName().compareToIgnoreCase(o2.getName());
    }
  };

  private static final Date ourDumpDay = new Date();

  private final List<DumpSpec> mySpecs;
  private final HttpMaterial myMaterial;

  @Nullable
  private Boolean mySuccess = null;

  @Nullable
  private Throwable myException = null;

  @Nullable
  private List<NameValuePair> myPostParameters = null;

  @Nullable
  private String myApplicationMessage = null;

  @Nullable
  private String myRequestUrl = null;

  @Nullable
  private String myResponse = null;

  @Nullable
  private String myScript = null;

  @Nullable
  private byte[] myRawRequest = null;

  @Nullable
  private Map<String, String> myHeaders = null;

  @Nullable
  private LogPrivacyPolizei myPolizei = null;

  @Nullable
  private Map<String, String> myResponseHeaders = null;

  @Nullable
  private List<String> myHttpReport = null;

  @Nullable
  private List<String> myCookiesBeforeRequest;

  public HttpDumper(HttpMaterial material, List<DumpSpec> specs) {
    mySpecs = specs;
    myMaterial = material;
  }

  public synchronized void clear() {
    myApplicationMessage = null;
    myException = null;
    myPostParameters = null;
    myRequestUrl = null;
    myResponse = null;
    myScript = null;
    mySuccess = null;
    myCookiesBeforeRequest = null;
  }

  public synchronized void dump() {
    try {
      dumpUnsafe();
    } catch (Exception e) {
      Log.warn(e);
      // ignore
    }
  }

  public synchronized void setException(Throwable exception) {
    myException = exception;
  }

  public synchronized void setUrl(String url) {
    myRequestUrl = url;
  }

  public synchronized void setPostParameters(Collection<NameValuePair> postParameters) {
    myPostParameters = postParameters == null ? null : Collections15.arrayList(postParameters);
  }

  public synchronized void setScriptOverride(String script) {
    myScript = script;
  }

  public synchronized void setResponse(String loadedResponse) {
    myResponse = loadedResponse;
  }

  public synchronized void setSuccess(boolean success) {
    mySuccess = success;
  }

  private void dumpUnsafe() {
    if (mySpecs == null || mySpecs.isEmpty()) {
      return;
    }

    boolean success = mySuccess != null && mySuccess;

    for(final DumpSpec spec : mySpecs) {
      if(spec.getLevel() == DumpLevel.NONE) {
        continue;
      }
      if(success && spec.getLevel() == DumpLevel.ERRORS) {
        continue;
      }
      File dumpFile = null;
      FileOutputStream stream = null;
      PrintStream out = null;
      try {
        dumpFile = getDumpFile(spec.getDir());
        assert dumpFile != null;
        stream = new FileOutputStream(dumpFile);
        out = new PrintStream(new BufferedOutputStream(stream));
        writeRequestData(out);
        writeHttpReport(out);
        writeReplyData(out, myResponse, myApplicationMessage, success, myException);
      } catch (DumpFileException e) {
        Log.debug("cannot create dump file: " + e.getMessage());
        return;
      } catch (IOException e) {
        Log.debug("failed to write dump to " + dumpFile);
      } finally {
        IOUtils.closeStreamIgnoreExceptions(out);
        IOUtils.closeStreamIgnoreExceptions(stream);
      }
    }
  }

  private File getDumpFile(File logDir) throws DumpFileException {
    if (logDir == null)
      throw new DumpFileException("no connector log dir");
    if (!logDir.isDirectory())
      logDir.mkdirs();
    if (!logDir.isDirectory())
      throw new DumpFileException("cannot create log dir");
    URI uri = getDumpURI();
    String host = getDumpHost(uri);
    File hostDir = makeDumpDir(logDir, host);
    String day = getDumpDayDirName();
    File dayDir = makeDumpDir(hostDir, day);
    String filename = getDumpFilename(uri);
    File file = new File(dayDir, filename);
    return file;
  }

  private URI getDumpURI() {
    URI uri;
    try {
      uri = myRequestUrl == null ? null : new URI(myRequestUrl, true);
    } catch (URIException e) {
      uri = null;
    }
    return uri;
  }

  private static String getDumpHost(URI uri) {
    String host;
    if (uri == null) {
      host = "unknown.host";
    } else {
      try {
        host = uri.getHost();
      } catch (URIException e) {
        host = "invalid.hostname";
      }
    }
    return host;
  }

  private String getDumpDayDirName() {
    String day;
    synchronized (DAY_DIR_FORMAT) {
      day = DAY_DIR_FORMAT.format(ourDumpDay);
    }
    return day;
  }

  private static File makeDumpDir(File parent, String dir) throws DumpFileException {
    File result = new File(parent, dir);
    if (!result.exists())
      result.mkdir();
    if (!result.isDirectory())
      throw new DumpFileException("can't create directory " + result);
    return result;
  }

  private String getDumpFilename(URI uri) {
    String filename;
    synchronized (FILENAME_FORMAT) {
      filename = FILENAME_FORMAT.format(new Date());
    }
    String suffix = myScript != null ? myScript : getDumpScript(uri);
    filename = filename + '.' + suffix;
    return filename;
  }

  private static String getDumpScript(URI uri) {
    String script;
    if (uri == null) {
      script = "unknown";
    } else {
      try {
        String path = Util.NN(uri.getPath());
        int k = path.lastIndexOf('/');
        if (k >= 0)
          path = path.substring(k + 1);
        k = path.indexOf('.');
        if (k >= 0)
          path = path.substring(0, k);
        if (path.length() == 0)
          path = "index";
        script = path;
      } catch (URIException e) {
        script = "invalid";
      }
    }
    return script;
  }

  private void writeRequestData(PrintStream out) {
    String timestamp;
    synchronized (TIMESTAMP_IN_FILE_FORMAT) {
      timestamp = TIMESTAMP_IN_FILE_FORMAT.format(new Date());
    }
    out.println("Dump time: " + timestamp);
    String url = myRequestUrl == null ? "unknown" : myRequestUrl;
    out.println("URL: " + privacy(url) + "\n");
    HttpState state = myMaterial.getHttpClient().getState();
    Cookie[] cookies = state.getCookies();
    List<String> beforeDump = myCookiesBeforeRequest;
    if (cookies.length > 0 || (beforeDump != null && !beforeDump.isEmpty())) {
      List<String> afterDump = HttpUtils.cookieDump(cookies);
      if (beforeDump == null || afterDump.equals(beforeDump)) {
        out.println("Cookies:");
        dumpCookies(out, afterDump);
      } else {
        out.println("Cookies Before:");
        dumpCookies(out, beforeDump);
        out.println("");
        out.println("Cookies After:");
        dumpCookies(out, afterDump);
      }
      out.println("");
    }
    Map<String, String> headers = myHeaders;
    if (headers != null) {
      out.println("Headers:");
      writeHeaders(headers, out);
    }
    List<NameValuePair> postParameters = myPostParameters;
    if (postParameters != null) {
      out.println("POST Parameters:");
      Collections.sort(postParameters, NVP_COMPARATOR);
      for (NameValuePair pair : postParameters) {
        out.println("   " + privacy(pair.getName() + "=" + pair.getValue()));
      }
      out.println("");
    }
    byte[] rawRequest = myRawRequest;
    if (rawRequest != null) {
      out.println("REQUEST (" + rawRequest.length + " bytes) :");
      out.println(privacy(new String(rawRequest)));
      out.println("---");
      out.println("");
    }
    URI uri = getDumpURI();
    String query = null;
    if (uri != null)
      query = uri.getEscapedQuery();

    if (query != null && query.length() > 0) {
      String[] params = query.split("\\&");
      if (params != null && params.length > 0) {
        out.println("GET Parameters:");
        for (String param : params) {
          out.println("   " + privacy(param));
        }
      }
    }

    out.println();
    out.println();
  }

  private void dumpCookies(PrintStream out, List<String> afterDump) {
    for (String string : afterDump) {
      string = privacy(string);
      out.print("   ");
      out.println(string);
    }
  }

  private void writeHttpReport(PrintStream out) {
    List<String> report;
    synchronized (this) {
      report = myHttpReport;
      myHttpReport = null;
    }
    if (report != null) {
      out.println("++++++++++++++++++ HTTP report ++++++++++++++++++");
      for (String s : report) {
        out.println(s);
      }
      out.println();
      out.println("+++++++++++++++++++++++++++++++++++++++++++++++++");
      out.println();
    }
  }

  private void writeHeaders(Map<String, String> headers, PrintStream out) {
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      out.println("   " + privacy(entry.getKey() + ": " + entry.getValue()));
    }
    out.println();
  }

  private String privacy(String string) {
    String s = string;
    LogPrivacyPolizei polizei = myPolizei;
    if (polizei != null) {
      s = polizei.examine(s);
    }
    return GlobalLogPrivacy.examineLogString(s);
  }

  private void writeReplyData(PrintStream out, String reply, String message, boolean success, Throwable exception) {
    out.println("Reply: " + (success ? "successful" : "failed"));
    if (message != null) {
      out.println("Message: " + message);
    }
    if (exception != null) {
      out.println("Exception: " + exception);
      exception.printStackTrace(out);
    }
    out.println();
//    if (myResponseHeaders != null) {
//      out.println("Response Headers:");
//      writeHeaders(myResponseHeaders, out);
//    }
    out.println("================================================================================");
    out.println();
    if (reply == null)
      out.println("-- There was no response --");
    else
      out.print(privacy(reply));
  }

  public synchronized void setMessage(String message) {
    myApplicationMessage = message;
  }

  public void setHeaders(Map<String, String> headers) {
    myHeaders = headers == null || headers.size() == 0 ? null : Collections15.hashMap(headers);
  }

  public void setRawRequest(byte[] bytes) {
    myRawRequest = bytes;
  }

  public void setPrivacyPolizei(LogPrivacyPolizei polizei) {
    myPolizei = polizei;
  }

  public void setResponseHeaders(Map<String, String> headers) {
    myResponseHeaders = headers == null || headers.size() == 0 ? null : Collections15.hashMap(headers);
  }

  public void report(String method, URI uri, HttpVersion version, Header[] requestHeaders, StatusLine response,
    Header[] responseHeaders)
  {
    List<String> report;
    synchronized (this) {
      if (myHttpReport == null)
        myHttpReport = Collections15.arrayList();
      report = myHttpReport;
    }
    String url = uri == null ? "?" : uri.getEscapedURI();
    report.add("");
    report.add(method + " " + privacy(url) + " " + version);
    if (requestHeaders != null) {
      for (Header header : requestHeaders) {
        report.add("   " + privacy(String.valueOf(header).trim()));
      }
    }
    if (response != null) {
      report.add("");
      report.add(Util.NN(String.valueOf(response)).trim());
      if (responseHeaders != null) {
        for (Header header : responseHeaders) {
          report.add("   " + privacy(String.valueOf(header).trim()));
        }
      }
    }
  }

  public void saveCookiesBeforeRequest() {
    Cookie[] cookies = myMaterial.getHttpClient().getState().getCookies();
    myCookiesBeforeRequest = HttpUtils.cookieDump(cookies);
  }


  public static final class DumpLevel extends Enumerable {
    public static final DumpLevel ALL = new DumpLevel("ALL");
    public static final DumpLevel ERRORS = new DumpLevel("ERRORS");
    public static final DumpLevel NONE = new DumpLevel("NONE");

    private DumpLevel(String name) {
      super(name);
    }
  }


  public static class DumpSpec {
    private final DumpLevel myLevel;
    private final File myDir;

    public DumpSpec(DumpLevel level, File dir) {
      myLevel = level;
      myDir = dir;
    }

    public DumpLevel getLevel() {
      return myLevel;
    }

    public File getDir() {
      return myDir;
    }

    public static List<DumpSpec> listOfTwo(DumpSpec spec1, DumpSpec spec2) {
      if(spec1 == null && spec2 == null) {
        return null;
      }
      if(spec1 != null) {
        return spec2 == null ? Collections.singletonList(spec1) : Collections15.arrayList(spec1, spec2);
      }
      return Collections.singletonList(spec2);
    }

    public static List<DumpSpec> listOfOne(DumpLevel level, File dumpDir) {
      if(level != null && level != DumpLevel.NONE && dumpDir != null) {
        return Collections.singletonList(new DumpSpec(level, dumpDir));
      }
      return null;
    }
  }


  private static class DumpFileException extends Exception {
    public DumpFileException(String message) {
      super(message);
    }
  }
}
