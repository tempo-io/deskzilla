package com.almworks.database.migration;

/**
 * :todoc:
 *
 * @author sereda
 */
public class MigrationFailure extends Throwable {
  public MigrationFailure(String message) {
    super(message);
  }

  public MigrationFailure(String message, Throwable cause) {
    super(message, cause);
  }
}
