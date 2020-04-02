package com.almworks.api.connector.http;

import com.almworks.api.http.HttpResponseData;
import com.almworks.util.io.StreamTransferTracker;
import com.almworks.util.io.StringTransferTracker;
import org.apache.commons.httpclient.URI;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
* @author Igor Sereda
*/
class TestResponseData implements HttpResponseData {
  private final String myResponse;

  public TestResponseData(String response) {
    myResponse = response;
  }

  @Override
  public String getContentFilename() {
    return null;
  }

  @Override
  public String getContentType() {
    return null;
  }

  @Override
  public String getFullContentType() {
    return null;
  }

  @Override
  public long getContentLength() {
    return myResponse.length();
  }

  @Override
  public long transferToStream(OutputStream output, @Nullable StreamTransferTracker tracker) throws IOException {
    try {
      new OutputStreamWriter(output, "UTF-8").append(myResponse).close();
      return myResponse.length();
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public String transferToString(StringTransferTracker tracker) throws IOException {
    return myResponse;
  }

  @Override
  public byte[] transferToBytes(StreamTransferTracker transferTracker) throws IOException {
    return myResponse.getBytes("UTF-8");
  }

  @NotNull
  @Override
  public Map<String, String> getResponseHeaders() {
    return new HashMap<String, String>();
  }

  @Override
  public URI getLastURI() {
    return null;
  }
}
