package com.almworks.api.database;

public class InvalidItemKeyException extends Exception {
  public InvalidItemKeyException() {
  }

  public InvalidItemKeyException(Throwable cause) {
    super(cause);
  }

  public InvalidItemKeyException(String message) {
    super(message);
  }

  public InvalidItemKeyException(String message, Throwable cause) {
    super(message, cause);
  }
}
