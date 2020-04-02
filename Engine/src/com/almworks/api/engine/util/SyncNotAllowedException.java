package com.almworks.api.engine.util;

import com.almworks.api.connector.ConnectorException;

/**
 * An excpetion thrown to indicate that synchronization
 * has been disabled for a connection.
 */
public class SyncNotAllowedException extends ConnectorException {
  public SyncNotAllowedException(String reason) {
    super(reason, "Synchronization is disabled", reason);
  }

  @Override
  public String getMediumDescription() {
    return getLongDescription();
  }
}
