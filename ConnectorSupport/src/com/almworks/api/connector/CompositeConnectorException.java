package com.almworks.api.connector;

import org.almworks.util.Collections15;

import java.util.*;

public class CompositeConnectorException extends ConnectorException {
  private final List<ConnectorException> myCauses;

  private CompositeConnectorException(String message, String shortDescription, String longDescription,
    Collection<ConnectorException> exceptions)
  {
    super(message, shortDescription, longDescription);
    myCauses = Collections.unmodifiableList(Collections15.arrayList(exceptions));
  }

  public static CompositeConnectorException create(Collection<ConnectorException> exceptions) {
    StringBuffer msg = new StringBuffer();
    StringBuffer shortMsg = new StringBuffer();
    StringBuffer longMsg = new StringBuffer("There were multiple problems when working with the server.");
    for (ConnectorException exception : exceptions) {
      if (shortMsg.length() > 0)
        shortMsg.append("; ");
      shortMsg.append(exception.getShortDescription());
      
      if (msg.length() > 0)
        msg.append("; ");
      msg.append(exception.getMessage());

      longMsg.append("\n\n");
      longMsg.append(exception.getLongDescription());
    }
    String shortDescription = shortMsg.toString();
    String longDescription = longMsg.toString();
    return new CompositeConnectorException(shortDescription, shortDescription, longDescription, exceptions);
  }

  public List<ConnectorException> getCauses() {
    return myCauses;
  }

  public static void rethrowExceptions(Collection<ConnectorException> exceptions) throws ConnectorException {
    if (exceptions != null) {
      int size = exceptions.size();
      if (size > 0) {
        if (size == 1) {
          throw exceptions.iterator().next();
        } else {
          throw create(exceptions);
        }
      }
    }
  }
}
