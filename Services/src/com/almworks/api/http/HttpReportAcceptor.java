package com.almworks.api.http;

import org.apache.commons.httpclient.*;

public interface HttpReportAcceptor {
  void report(String method, URI uri, HttpVersion version, Header[] requestHeaders, StatusLine response,
    Header[] responseHeaders);
}
