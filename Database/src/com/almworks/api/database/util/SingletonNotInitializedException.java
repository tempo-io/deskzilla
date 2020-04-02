package com.almworks.api.database.util;

public class SingletonNotInitializedException extends IllegalStateException {
  public SingletonNotInitializedException() {
    super();
  }

  public SingletonNotInitializedException(String s) {
    super(s);
  }
}
