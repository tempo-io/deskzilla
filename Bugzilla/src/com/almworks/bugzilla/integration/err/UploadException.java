package com.almworks.bugzilla.integration.err;

import com.almworks.api.connector.ConnectorException;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import org.almworks.util.Collections15;

import java.util.List;

public class UploadException extends ConnectorException {
  private final List<BugzillaAttribute> myCauseAttributes;

  public UploadException(String message, Throwable cause, String shortDescription, String longDescription) {
    super(message, cause, shortDescription, longDescription);
    myCauseAttributes = Collections15.emptyList();
  }

  public UploadException(String message, String shortDescription, String longDescription) {
    super(message, shortDescription, longDescription);
    myCauseAttributes = Collections15.emptyList();
  }

  public List<BugzillaAttribute> getCauseAttributes() {
    return myCauseAttributes;
  }
}
