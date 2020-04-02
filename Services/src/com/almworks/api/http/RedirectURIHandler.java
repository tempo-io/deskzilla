package com.almworks.api.http;

import org.apache.commons.httpclient.URI;
import org.jetbrains.annotations.*;

public interface RedirectURIHandler {
  @Nullable
  URI approveRedirect(URI initialUri, URI redirectUri) throws HttpLoaderException; 
}
