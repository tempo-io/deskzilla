package com.almworks.api.connector.http;

import com.almworks.util.L;

public class HttpFailureConnectionException extends ConnectionException {
  private final int myStatusCode;
  private final String myStatusText;

  public HttpFailureConnectionException(String url, int statusCode, String statusText) {
    super(url,
      "server responded [" + statusCode + " " + statusText + "]",
      null,
      L.tooltip("Server HTTP response [" + statusCode + " " + statusText + "]"));
    myStatusCode = statusCode;
    myStatusText = statusText;
  }

  public int getStatusCode() {
    return myStatusCode;
  }

  public String getStatusText() {
    return myStatusText;
  }
}
