package com.almworks.bugzilla.integration.err;

import com.almworks.api.connector.ConnectorException;

public class BugzillaAccessException extends ConnectorException {
  public BugzillaAccessException(String message, String shortDescription, String longDescription) {
    super(message, shortDescription, longDescription);
  }

  public BugzillaAccessException(String message, Throwable cause, String shortDescription, String longDescription) {
    super(message, cause, shortDescription, longDescription);
  }
}
