package com.almworks.bugzilla.integration.err;

import com.almworks.api.connector.ConnectorException;
import com.almworks.util.L;

/**
 * @author Vasya
 */
public class TimeoutException extends ConnectorException {
  public TimeoutException(String message, String shortDescription, long timeout) {
    super(message, shortDescription, L.tooltip("Operation time exceed " + timeout + "ms"));
  }
}
