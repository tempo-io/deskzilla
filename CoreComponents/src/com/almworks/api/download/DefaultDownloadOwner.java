package com.almworks.api.download;

import com.almworks.api.http.*;
import com.almworks.util.exec.Context;
import org.apache.commons.httpclient.HttpMethodBase;

import java.io.File;

public class DefaultDownloadOwner implements DownloadOwner {
  public static final DownloadOwner INSTANCE = new DefaultDownloadOwner();

  public String getDownloadOwnerID() {
    return "default";
  }

  public HttpLoader createLoader(final String argument, boolean retrying) throws CannotCreateLoaderException {
    HttpClientProvider clientProvider = Context.get(HttpClientProvider.ROLE);
    if (clientProvider == null)
      clientProvider = HttpClientProvider.SIMPLE;
    HttpLoaderFactory loaderFactory = Context.get(HttpLoaderFactory.ROLE);
    if (loaderFactory == null)
      loaderFactory = HttpLoaderFactory.SIMPLE;
    DefaultHttpMaterial material = new DefaultHttpMaterial(clientProvider, loaderFactory);
    return material.createLoader(argument, new HttpMethodFactory() {
      public HttpMethodBase create() throws HttpMethodFactoryException {
        return HttpUtils.createGet(argument);
      }
    });
  }

  public boolean isValid() {
    return true;
  }

  public void validateDownload(String argument, File downloadedFile, String mimeType) throws DownloadInvalidException {
  }
}
