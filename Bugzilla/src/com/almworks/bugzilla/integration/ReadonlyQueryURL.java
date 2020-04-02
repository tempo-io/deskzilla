package com.almworks.bugzilla.integration;

public class ReadonlyQueryURL implements QueryURL {
  private final String myUrl;

  public ReadonlyQueryURL(String url) {
    myUrl = url;
  }

  @Override
  public String getURL() throws IllegalStateException {
    return myUrl;
  }
}
