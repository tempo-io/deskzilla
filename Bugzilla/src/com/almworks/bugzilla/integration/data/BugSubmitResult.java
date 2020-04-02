package com.almworks.bugzilla.integration.data;

import com.almworks.api.connector.ConnectorException;

/**
 * :todoc:
 *
 * @author sereda
 */
public class BugSubmitResult {
  private final Integer myID;
  private final ConnectorException myException;
  private final int myPostCount;

  public BugSubmitResult(Integer ID, ConnectorException exception, int postCount) {
    assert ID != null || exception != null;
    myException = exception;
    myID = ID;
    myPostCount = postCount;
  }

  public ConnectorException getException() {
    return myException;
  }

  public Integer getID() {
    return myID;
  }

  public int getPostCount() {
    return myPostCount;
  }
}
