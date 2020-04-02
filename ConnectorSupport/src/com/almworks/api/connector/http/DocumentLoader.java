package com.almworks.api.connector.http;

import com.almworks.api.connector.*;
import com.almworks.api.http.*;
import com.almworks.util.*;
import com.almworks.util.collections.MultiMap;
import com.almworks.util.commons.Condition;
import com.almworks.util.files.FileUtil;
import com.almworks.util.io.IOUtils;
import com.almworks.util.io.StringTransferTracker;
import com.almworks.util.xml.JDOMUtils;
import com.almworks.util.xml.ZeroEntityResolver;
import org.almworks.util.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.methods.multipart.*;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.*;

import static org.almworks.util.Collections15.arrayList;

/**
 * Utility class for building XML documents from URLs.
 *
 * @author sereda
 */
public class DocumentLoader {
  private final File myFile;
  @Nullable
  private final HttpDumper myDumper;
  private final HttpMaterial myHttpMaterial;
  private final String myEscapedUrl;

  private HttpResponseData myRawResponse;
  private String myResponse;
  private byte[] myResponseBytes;

  private StringTransferTracker myTransferTracker;
  private Condition<Integer> myFailedStatusApprover;
  private List<RedirectURIHandler> myRedirectUriHandlers;
  private URI myLastURI;
  private boolean myNoRetries;

  @Nullable
  private List<Header> myAuxiliaryHeaders;

  public DocumentLoader(HttpMaterial material, String escapedUrl, List<HttpDumper.DumpSpec> specs) {
    myHttpMaterial = material;
    myEscapedUrl = escapedUrl;
    myFile = null;
    if (specs != null && !specs.isEmpty()) {
      myDumper = new HttpDumper(material, specs);
      myDumper.setUrl(escapedUrl);
    } else
      myDumper = null;
  }

  public DocumentLoader(HttpMaterial material, File file) {
    myHttpMaterial = material;
    myEscapedUrl = null;
    myFile = file;
    myDumper = null;
  }

  // for tests
  DocumentLoader(HttpMaterial material) {
    myHttpMaterial = material;
    myEscapedUrl = "http://almworks.com/testing";
    myFile = null;
    myDumper = null;
  }

  public synchronized DocumentLoader noRetries() {
    myNoRetries = true;
    return this;
  }

  public synchronized boolean hasResponse() {
    return myResponse != null || myRawResponse != null;
  }

  public synchronized DocumentLoader httpGET() throws ConnectorException {
    if (myEscapedUrl == null) {
      assert false : this;
      return this;
    }
    assert !hasResponse();
    if (hasResponse())
      return this;
    try {
      myRawResponse = doGET();
      assert hasResponse();
      return this;
    } catch (IOException e) {
      Log.debug("connection failure", e);
      throw new ConnectionException(myEscapedUrl, "connection failure", e);
    }
  }

  // for testing
  synchronized DocumentLoader setResponse(HttpResponseData response) throws ConnectorException {
    if (myEscapedUrl == null) {
      assert false : this;
      return this;
    }
    assert !hasResponse();
    if (hasResponse())
      return this;
    myRawResponse = response;
    assert hasResponse();
    return this;
  }

  public synchronized DocumentLoader httpMultipartPOST(MultiMap<String, String> parametersMap, String attachName,
    File attachFile, String attachContentType) throws ConnectorException
  {
    assert myEscapedUrl != null;
    assert !hasResponse();
    if (hasResponse())
      return this;
    List<NameValuePair> parameters = convertToNVP(parametersMap);
    assert dumpPostParameters(myEscapedUrl, parameters);
    assert dumpFileUploadParams(attachName, attachFile, attachContentType);
    try {
      myRawResponse = doMultipartPOST(parameters, attachName, attachFile, attachContentType);
      assert hasResponse();
      return this;
    } catch (IOException e) {
      Log.warn("connection failure", e);
      throw new ConnectionException(myEscapedUrl, "connection failure", e);
    }
  }

  public synchronized DocumentLoader httpPOST(Collection<NameValuePair> parameters) throws ConnectorException {
    return httpPOST(parameters, false);
  }

  public synchronized DocumentLoader httpPOST(MultiMap<String, String> parameters) throws ConnectorException {
    return httpPOST(parameters, false);
  }

  public synchronized DocumentLoader httpPOST(MultiMap<String, String> parameters, boolean useGetOnRedirect)
    throws ConnectorException
  {
    List<NameValuePair> list = convertToNVP(parameters);
    return httpPOST(list, useGetOnRedirect);
  }

  private List<NameValuePair> convertToNVP(MultiMap<String, String> parameters) {
    List<NameValuePair> list = Collections15.arrayList();
    for (Pair<String, String> parameter : parameters) {
      list.add(new NameValuePair(parameter.getFirst(), parameter.getSecond()));
    }
    return list;
  }

  public synchronized DocumentLoader httpPOST(Collection<NameValuePair> parameters, boolean useGetOnRedirect)
    throws ConnectorException
  {
    assert myEscapedUrl != null;
    assert !hasResponse();
    if (hasResponse())
      return this;
    try {
      myRawResponse = doPOST(parameters, useGetOnRedirect);
      assert hasResponse();
      return this;
    } catch (IOException e) {
      Log.debug("connection failure", e);
      throw new ConnectionException(myEscapedUrl, "connection failure", e);
    }
  }

  public synchronized DocumentLoader httpPOST(@Nullable Map<String, String> headers, @NotNull RequestEntity rawRequest)
    throws ConnectorException
  {
    assert myEscapedUrl != null;
    assert !hasResponse();
    if (hasResponse())
      return this;
    try {
      myRawResponse = doRawPOST(headers, rawRequest);
      assert hasResponse();
      return this;
    } catch (IOException e) {
      Log.debug("connection failure", e);
      throw new ConnectionException(myEscapedUrl, "connection failure", e);
    }
  }

  /**
   * Instead of httpGet, load response from a file. Useful for testing.
   */
  public synchronized DocumentLoader fileLoad() throws ConnectorException {
    assert myFile != null;
    assert !hasResponse();
    FileInputStream stream = null;
    try {
      String charset = myHttpMaterial.getCharset();
      if (charset == null)
        myResponse = FileUtil.readFile(myFile);
      else
        myResponse = FileUtil.readFile(myFile.getPath(), charset);
      return this;
    } catch (IOException e) {
      throw new ConnectorException("cannot load " + myFile, e, "", "");
    } finally {
      IOUtils.closeStreamIgnoreExceptions(stream);
    }
  }

  @NotNull
  public synchronized Document loadHTML() throws ConnectorException {
    assert hasResponse();
    if (!hasResponse())
      return new Document();
    String response = null;
    try {
      response = getStringResponse();
//    Reader reader = new XML10CorrectingStringReader(myResponse);
      return parseHTML(new InputSource(new StringReader(response)));
    } catch (IOException e) {
      Log.debug("load failure", e);
      throw new ConnectionException(myEscapedUrl, "load failure", e);
    } catch (SAXException e) {
      Log.warn("cannot parse html output:\n----------[ " + myEscapedUrl + " ]----------\n" + response +
        "\n-------------------------------------");
      Log.warn(e);
      throw new CannotParseException(myEscapedUrl, "cannot parse html output", e);
    }
  }

  public synchronized Document loadXML() throws ConnectorException {
    assert hasResponse();
    if (!hasResponse())
      return new Document();
    String response = null;
    try {
      response = getStringResponse();
//    Reader reader = new XML10CorrectingStringReader(myResponse);
      return parseXML(new InputSource(new StringReader(response)));
    } catch (IOException e) {
      Log.warn("connection failure", e);
      throw new ConnectionException(myEscapedUrl, "connection failure", e);
    } catch (JDOMException e) {
      Log.warn("cannot parse xml output:\n----------[ " + myEscapedUrl + " ]----------\n" + response +
        "\n-------------------------------------");
      Log.warn(e);
      throw new CannotParseException(myEscapedUrl, "cannot parse xml", e);
    }
  }

  public synchronized String loadString() throws ConnectorException {
    assert hasResponse() : this;
    if (!hasResponse())
      return "";
    try {
      return getStringResponse();
    } catch (IOException e) {
      Log.warn("connection failure", e);
      throw new ConnectionException(myEscapedUrl, "connection failure", e);
    }
  }

  public synchronized byte[] loadBytes() throws IOException {
    assert hasResponse();
    if (!hasResponse())
      return Const.EMPTY_BYTES;
    if (myRawResponse != null) {
      return getResponseBytes();
    } else {
      assert myResponse != null;
      return myResponse.getBytes("UTF-8");
    }
  }

  public synchronized long getResponseContentLength() {
    if (myRawResponse == null) {
      assert false : myEscapedUrl;
      return 0;
    }
    return myRawResponse.getContentLength();
  }

  @Nullable
  public synchronized String getResponseContentType() {
    if (myRawResponse == null) {
      assert false : myEscapedUrl;
      return "";
    }
    return myRawResponse.getContentType();
  }

  @Nullable
  public synchronized String getResponseFullContentType() {
    if (myRawResponse == null) {
      assert false : myEscapedUrl;
      return "";
    }
    return myRawResponse.getFullContentType();
  }

  public synchronized Map<String, String> getResponseHeaders() {
    if (myRawResponse == null) {
      assert false : myEscapedUrl;
      return Collections15.emptyMap();
    }
    return myRawResponse.getResponseHeaders();
  }

  private byte[] getResponseBytes() throws IOException {
    assert myRawResponse != null;
    if (myResponseBytes == null) {
      myResponseBytes = transferBytes(myRawResponse);
    }
    return myResponseBytes;
  }

  private InputSource getResponseInputSource() throws IOException {
    String response = getStringResponse();
//    Reader reader = new XML10CorrectingStringReader(myResponse);
    Reader reader = new StringReader(response);
    return new InputSource(reader);
  }

  private String getStringResponse() throws IOException {
    assert myRawResponse != null || myResponse != null;
    if (myResponse == null) {
      myResponse = transferString(myRawResponse);
    }
    String response = myResponse;
    return response;
  }

  public synchronized void setTransferTracker(StringTransferTracker tracker) {
    myTransferTracker = tracker;
  }

  private HttpResponseData doGET() throws IOException, ConnectorException {
    HttpLoader loader = myHttpMaterial.createLoader(myEscapedUrl, new HttpMethodFactory() {
      public HttpMethodBase create() throws HttpMethodFactoryException {
        GetMethod method = null;
        method = HttpUtils.createGet(myEscapedUrl);
        fixUrl(method, myEscapedUrl);
        addAuxiliaryHeaders(method);
        return method;
      }
    });
    return requestRaw(loader);
  }

  private static void fixUrl(GetMethod method, String url) {
    try {
      URI uri = new URI(url, true);
      boolean hasIllegal = uri.getHost().indexOf('_') >= 0;
      if (hasIllegal) {
        int schemeEnd = url.indexOf("://");
        int hostStart = schemeEnd < 0 ? 0 : schemeEnd + 3;
        int hostEnd = url.indexOf("/", hostStart);
        String hostAndPort = hostEnd > 0 ? url.substring(hostStart, hostEnd) : url.substring(hostStart);
        int colon = hostAndPort.indexOf(':');
        String host = colon < 0 ? hostAndPort : hostAndPort.substring(0, colon);
        String portString = colon < 0 ? null : hostAndPort.substring(colon + 1);
        if (host.length() > 0) {
          final int overridePort = Util.toInt(portString, -1);
          URI newUri = new URI(url, true) {
            public int getPort() {
              return overridePort;
            }
          };
          method.setURI(newUri);
        }
      }
    } catch (URIException e) {
      // ignore
      Log.debug(e);
    }
  }

  private String transferString(@NotNull HttpResponseData response) throws IOException {
    String string = response.transferToString(myTransferTracker);
    // save memory
    myTransferTracker = null;
    if (myDumper != null) {
      myDumper.setResponse(string);
    }
    return string;
  }

  private byte[] transferBytes(@NotNull HttpResponseData response) throws IOException {
    // todo transfer tracker
    byte[] bytes = response.transferToBytes(null);
    // save memory
    myTransferTracker = null;
    if (myDumper != null) {
      myDumper.setResponse(new String(bytes));
    }
    return bytes;
  }

  private HttpResponseData requestRaw(HttpLoader loader)
    throws IOException, CancelledException, HttpFailureConnectionException
  {
    try {
      if (myFailedStatusApprover != null) {
        loader.setFailedStatusApprover(myFailedStatusApprover);
      }
      if (myRedirectUriHandlers != null) {
        for (RedirectURIHandler redirectUriHandler : myRedirectUriHandlers) {
          loader.addRedirectUriHandler(redirectUriHandler);
        }
      }
      if (myDumper != null) {
        loader.setReportAcceptor(myDumper);
        myDumper.saveCookiesBeforeRequest();
      }
      if (myNoRetries) {
        loader.setRetries(1);
      }
      HttpResponseData response = loader.load();
      if (myDumper != null) {
        Map<String, String> headers = response.getResponseHeaders();
        if (headers.size() > 0) {
          myDumper.setResponseHeaders(headers);
        }
      }
      myLastURI = response.getLastURI();
      return response;
    } catch (HttpCancelledException e) {
      throw new CancelledException(e);
    } catch (HttpConnectionException e) {
      LogHelper.warning("Connection exception", e.getStatusCode(), e.getStatusText(), myEscapedUrl);
      throw new HttpFailureConnectionException(myEscapedUrl, e.getStatusCode(), e.getStatusText());
    } catch (HttpLoaderException e) {
      throw new ConnectorLoaderException(e);
    }
  }

  private HttpResponseData doMultipartPOST(final Collection<NameValuePair> parameters, final String attachName,
    final File attachFile, final String attachContentType)
    throws CancelledException, IOException, HttpFailureConnectionException
  {
    if (myDumper != null) {
      myDumper.setPostParameters(parameters);
      // todo dump file parameters also
    }
    HttpLoader loader = myHttpMaterial.createLoader(myEscapedUrl, new HttpMethodFactory() {
      public HttpMethodBase create() throws HttpMethodFactoryException {
        try {
          PostMethod post = HttpUtils.createPost(myEscapedUrl);
          Part[] parts = new Part[parameters.size() + 1];
          int i = 0;
          String charset = myHttpMaterial.getCharset();
          for (Iterator<NameValuePair> ii = parameters.iterator(); ii.hasNext();) {
            NameValuePair pair = ii.next();
            parts[i++] = new StringPart(pair.getName(), pair.getValue(), charset);
          }
          assert i == parts.length - 1;
          parts[i] = new FilePart(attachName, attachFile, attachContentType, charset);
          post.setRequestEntity(new MultipartRequestEntity(parts, post.getParams()));
          return post;
        } catch (FileNotFoundException e) {
          throw new HttpMethodFactoryException("cannot attach file: " + e.getMessage(), e);
        }
      }
    });
    return requestRaw(loader);
  }


  private HttpResponseData doPOST(final Collection<NameValuePair> parameters, boolean useGetOnRedirect)
    throws ConnectorException, IOException
  {
    if (myDumper != null) {
      myDumper.setPostParameters(parameters);
    }
    HttpLoader loader = myHttpMaterial.createLoader(myEscapedUrl, new HttpMethodFactory() {
      public HttpMethodBase create() throws HttpMethodFactoryException {
        PostMethod post = HttpUtils.createPost(myEscapedUrl);
        post.addParameters(parameters.toArray(new NameValuePair[parameters.size()]));
        addAuxiliaryHeaders(post);
        return post;
      }
    });
    if (useGetOnRedirect) {
      loader.setRedirectMethodFactory(new HttpMethodFactory() {
        public HttpMethodBase create() throws HttpMethodFactoryException {
          return HttpUtils.createGet(myEscapedUrl);
        }
      });
    }
    return requestRaw(loader);
  }

  private void addAuxiliaryHeaders(HttpMethodBase method) {
    if (myAuxiliaryHeaders != null) {
      for (Header header : myAuxiliaryHeaders) {
        method.addRequestHeader(header);
      }
    }
  }

  public void addHeader(String name, String value) {
    if (myAuxiliaryHeaders == null)
      myAuxiliaryHeaders = arrayList();
    //noinspection ConstantConditions
    myAuxiliaryHeaders.add(new Header(name, value));
  }

  private HttpResponseData doRawPOST(@Nullable Map<String, String> headers, @NotNull final RequestEntity rawRequest)
    throws ConnectorException, IOException
  {
    final Map<String, String> finalHeaders =
      headers == null || headers.size() == 0 ? null : Collections15.hashMap(headers);
    if (myDumper != null) {
      if (headers != null) {
        myDumper.setHeaders(finalHeaders);
      }
      if (rawRequest.isRepeatable()) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        rawRequest.writeRequest(out);
        myDumper.setRawRequest(out.toByteArray());
      } else {
        // todo: buffer?
      }
    }
    HttpLoader loader = myHttpMaterial.createLoader(myEscapedUrl, new HttpMethodFactory() {
      public HttpMethodBase create() throws HttpMethodFactoryException {
        PostMethod post = HttpUtils.createPost(myEscapedUrl);
        if (finalHeaders != null) {
          for (Map.Entry<String, String> entry : finalHeaders.entrySet()) {
            post.addRequestHeader(entry.getKey(), entry.getValue());
          }
        }
        post.setRequestEntity(rawRequest);
        return post;
      }
    });
    return requestRaw(loader);
  }

  @NotNull
  private Document parseHTML(InputSource content) throws IOException, SAXException, CancelledException {
    try {
      myHttpMaterial.checkCancelled();
      Document document = HtmlUtils.buildHtmlDocument(content);
      myHttpMaterial.checkCancelled();
      return document;
    } catch (HttpCancelledException e) {
      throw new CancelledException();
    }
  }

  private Document parseXML(InputSource source) throws CancelledException, IOException, JDOMException {
    try {
      myHttpMaterial.checkCancelled();
      SAXBuilder builder = JDOMUtils.createBuilder();
      builder.setEntityResolver(new ZeroEntityResolver());
      Document document = builder.build(source);
      myHttpMaterial.checkCancelled();
      return document;
    } catch (HttpCancelledException e) {
      throw new CancelledException();
    }
  }

  private static boolean dumpFileUploadParams(String attachName, File attachFile, String attachContentType) {
    Log.debug(
      "  " + attachName + " = FILE: " + attachFile.getPath() + " (" + attachContentType + "; " + attachFile.length() +
        " bytes)");
    return true;
  }

  private static boolean dumpPostParameters(String url, Collection<NameValuePair> parameters) {
    Log.debug("POST " + url);
    for (Iterator<NameValuePair> ii = parameters.iterator(); ii.hasNext();) {
      NameValuePair pair = ii.next();
      String name = pair.getName();
      boolean isPrivateInfo = Util.lower(name).indexOf("password") > -1; //hack about #586
      Log.debug("  " + name + " = " + (isPrivateInfo ? "**********" : pair.getValue()));
    }
    return true;
  }

  public synchronized void setSuccess(boolean success) {
    if (myDumper != null)
      myDumper.setSuccess(success);
  }

  public synchronized void setException(Throwable exception) {
    if (myDumper != null)
      myDumper.setException(exception);
  }

  public synchronized void finish() {
    if (myDumper != null) {
      myDumper.dump();
      myDumper.clear();
    }
    // todo any cleanup?
  }

  public synchronized void setScriptOverride(String script) {
    if (myDumper != null) {
      myDumper.setScriptOverride(script);
    }
  }

  public synchronized void setApplicationMessage(String message) {
    if (myDumper != null) {
      myDumper.setMessage(message);
    }
  }

  public void setLogPrivacyPolizei(LogPrivacyPolizei polizei) {
    if (myDumper != null) {
      myDumper.setPrivacyPolizei(polizei);
    }
  }

  public void setFailedStatusApprover(Condition<Integer> statusCodeApprover) {
    myFailedStatusApprover = statusCodeApprover;
  }

  public synchronized void addRedirectUriHandler(RedirectURIHandler handler) {
    List<RedirectURIHandler> handlers = myRedirectUriHandlers;
    if (handlers == null)
      handlers = myRedirectUriHandlers = Collections15.arrayList();
    handlers.add(handler);
  }

  @Nullable
  public URI getLastURI() {
    return myLastURI;
  }
}
