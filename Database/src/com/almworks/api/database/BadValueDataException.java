package com.almworks.api.database;

public class BadValueDataException extends RuntimeException {
  public BadValueDataException(String message) {
    super(message);
  }
}
