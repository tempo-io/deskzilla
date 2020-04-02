package com.almworks.api.http;

import com.almworks.util.commons.Condition;

import java.io.IOException;

public interface HttpLoader {
  String DEFAULT_CHARSET = "ISO-8859-1";

  void setMaximumRedirects(int maximumRedirects);

  void setRetries(int retries);

  HttpResponseData load() throws IOException, HttpLoaderException;

  HttpMaterial getMaterial();

  void setRedirectMethodFactory(HttpMethodFactory factory);

  void setFailedStatusApprover(Condition<Integer> failedStatusCodeApprover);

  void setReportAcceptor(HttpReportAcceptor reportAcceptor);

  void addRedirectUriHandler(RedirectURIHandler handler);
}
