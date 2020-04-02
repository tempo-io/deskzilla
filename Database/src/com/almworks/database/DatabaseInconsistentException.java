package com.almworks.database;

/**
 * :todoc:
 *
 * @author sereda
 */
public class DatabaseInconsistentException extends Exception {
  public DatabaseInconsistentException() {
    super();
  }

  public DatabaseInconsistentException(String message) {
    super(message);
  }

  public DatabaseInconsistentException(Throwable cause) {
    super(cause);
  }

  public DatabaseInconsistentException(String message, Throwable cause) {
    super(message, cause);
  }
}
