package com.almworks.bugzilla.integration.err;

import com.almworks.api.connector.ConnectorException;
import com.almworks.util.L;

public class BugzillaLoginRequiredException extends ConnectorException {
  public BugzillaLoginRequiredException() {
    this(null, null);
  }
  
  public BugzillaLoginRequiredException(String description, Throwable e) {
    super("bugzilla login required", e,
      L.content("Login is required"),
      L.content("Looks like Bugzilla is asking you to enter your \n" +
      "account name and password.\n\n" + (description == null ? "" : description)));
  }
}
