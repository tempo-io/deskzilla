package com.almworks.api.http;

import com.almworks.util.properties.Role;

public interface HttpLoaderFactory {
  Role<HttpLoaderFactory> ROLE = Role.role(HttpLoaderFactory.class);
  HttpLoaderFactory SIMPLE = new DumbHttpLoaderFactory();

  HttpLoader createLoader(HttpMaterial httpMaterial, HttpMethodFactory methodFactory, String escapedUrl);
}
