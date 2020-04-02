package com.almworks.http;

import com.almworks.api.http.HttpLoader;
import com.almworks.api.http.HttpResponseData;
import com.almworks.util.io.*;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.URI;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

public class HttpResponseDataImpl implements HttpResponseData {
  private String myCharset = HttpLoader.DEFAULT_CHARSET;
  private InputStream myContentStream;
  private String myString;
  private XMLCharValidator myCharValidator = XMLCharValidator.INSTANCE;
  private String myContentType;
  private String myFullContentType;
  private String myContentFilename;
  private long myContentLength = -1;
  private HashMap<String, String> myResponseHeaders = null;

  @Nullable
  private HttpMethodExecutor myExecutor;

  @Nullable
  private URI myLastURI;

  public HttpResponseDataImpl(HttpMethodExecutor executor) {
    myExecutor = executor;
  }

  public synchronized String transferToString(StringTransferTracker tracker) throws IOException {
    if (myString != null)
      return myString;
    assert myContentStream != null : this;
    if (myContentStream == null)
      return null;

    try {
      if (myContentLength > 0 && tracker != null) {
        tracker.setContentLengthHint(myContentLength);
      }

      try {
        myString = IOUtils.transferToString(myContentStream, myCharset, myCharValidator, tracker);
      } finally {
        IOUtils.closeStreamIgnoreExceptions(myContentStream);
        myContentStream = null;
      }

      return myString;
    } finally {
      releaseConnection();
    }
  }

  public byte[] transferToBytes(StreamTransferTracker transferTracker) throws IOException {
    try {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      transferToStream(stream, transferTracker);
      return stream.toByteArray();
    } finally {
      releaseConnection();
    }
  }

  // todo refactor - polymorphic transfer operation

  public long transferToStream(OutputStream output, @Nullable StreamTransferTracker tracker) throws IOException {
    assert output != null;
    assert myContentStream != null : this;
    if (myContentStream == null)
      return 0;

    try {
      return IOUtils.transfer(myContentStream, output, tracker);
    } finally {
      IOUtils.closeStreamIgnoreExceptions(myContentStream);
      myContentStream = null;
      releaseConnection();
    }
  }

  private void releaseConnection() {
    HttpMethodExecutor executor = myExecutor;
    if (executor != null) {
      try {
        executor.dispose();
      } catch (Exception e) {
        Log.debug("cannot release connection", e);
      }
      myExecutor = null;
    }
  }


  public void setCharset(String charset) {
    try {
      if (charset == null || charset.length() == 0 || !Charset.isSupported(charset)) {
        Log.warn("charset " + charset + " is not supported by the operating system, falling back to " + HttpLoader
          .DEFAULT_CHARSET);
        charset = HttpLoader.DEFAULT_CHARSET;
      }
    } catch (java.nio.charset.IllegalCharsetNameException e) {
      Log.warn("illegal charset: " + charset, e);
      charset = HttpLoader.DEFAULT_CHARSET;
    }
    myCharset = charset;
  }

  public void setContentStream(InputStream contentStream) {
    myContentStream = contentStream;
  }

  public String getContentType() {
    return myContentType;
  }

  public String getFullContentType() {
    return myFullContentType;
  }

  public long getContentLength() {
    return myContentLength;
  }

  public String getContentFilename() {
    return myContentFilename;
  }


  public Map<String, String> getResponseHeaders() {
    HashMap<String, String> headers = myResponseHeaders;
    if (headers == null) {
      return Collections15.emptyMap();
    } else {
      return Collections.unmodifiableMap(headers);
    }
  }

  public URI getLastURI() {
    return myLastURI;
  }

  public void setLastURI(URI lastURI) {
    myLastURI = lastURI;
  }

  void setResponseHeaders(Header[] headers) {
    myResponseHeaders = Collections15.hashMap();
    for (Header header : headers) {
      myResponseHeaders.put(header.getName(), header.getValue());
    }
  }

  void setContentType(String contentType) {
    myContentType = contentType;
  }

  void setFullContentType(String fullContentType) {
    myFullContentType = fullContentType;
  }

  void setContentFilename(String name) {
    myContentFilename = name;
  }

  void setContentLength(long length) {
    myContentLength = length;
  }

  static {
    assert Charset.isSupported(HttpLoader.DEFAULT_CHARSET);
  }
}
