package com.almworks.bugzilla.integration.err;

import com.almworks.api.connector.ConnectorException;

public class BugzillaResponseException extends ConnectorException {
  public BugzillaResponseException(String message, String shortDescription, String longDescription) {
    super(message, shortDescription, longDescription);
  }

  public BugzillaResponseException(String message, Throwable cause, String shortDescription, String longDescription) {
    super(message, cause, shortDescription, longDescription);
  }
}
