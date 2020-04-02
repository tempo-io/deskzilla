package com.almworks.bugzilla.integration.err;

import com.almworks.api.connector.ConnectorException;

public class BugzillaErrorException extends ConnectorException {
  public static BugzillaErrorException create(String process, String errorTitle, String errorDescr) {
    final String title = "Bugzilla: " + errorTitle;
    final String descr = "Bugzilla reported an error during " + process + ":\n\n" + errorDescr;
    return new BugzillaErrorException(title, descr, errorTitle, errorDescr);
  }

  private final String myErrorTitle;
  private final String myErrorDescription;

  public BugzillaErrorException(String message, String longDescription, String errorTitle, String errorDescription) {
    super(message, message, longDescription);
    myErrorTitle = errorTitle;
    myErrorDescription = errorDescription;
  }

  public String getErrorTitle() {
    return myErrorTitle;
  }

  public String getErrorDescription() {
    return myErrorDescription;
  }
}
