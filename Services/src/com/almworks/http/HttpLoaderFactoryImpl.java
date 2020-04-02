package com.almworks.http;

import com.almworks.api.http.*;

public class HttpLoaderFactoryImpl implements HttpLoaderFactory {
  public HttpLoader createLoader(HttpMaterial httpMaterial, HttpMethodFactory methodFactory, String escapedUrl) {
    return new HttpLoaderImpl(httpMaterial, methodFactory, escapedUrl);
  }
}
