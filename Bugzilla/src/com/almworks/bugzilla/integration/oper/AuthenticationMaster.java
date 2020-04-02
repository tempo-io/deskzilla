package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;

public interface AuthenticationMaster {
  void reauthenticate() throws ConnectorException;

  void clearAuthentication();
}
