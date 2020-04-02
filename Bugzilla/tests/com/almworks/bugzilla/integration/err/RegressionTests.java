package com.almworks.bugzilla.integration.err;

import com.almworks.api.connector.http.CannotParseException;
import com.almworks.util.tests.BaseTestCase;

public class RegressionTests extends BaseTestCase {
  public void testNPEinExceptionConstructor() {
    new CannotParseException("http://whereever", "a message");
  }
}
