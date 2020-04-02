package com.almworks.api.download;

import com.almworks.api.http.HttpLoader;

import java.io.File;

public interface DownloadOwner {
  String getDownloadOwnerID();

  HttpLoader createLoader(String argument, boolean retrying) throws CannotCreateLoaderException;

  boolean isValid();

  void validateDownload(String argument, File downloadedFile, String mimeType) throws DownloadInvalidException;
}
