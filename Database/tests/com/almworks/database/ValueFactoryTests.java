package com.almworks.database;

import com.almworks.api.database.Value;

import java.io.ByteArrayInputStream;

public class ValueFactoryTests extends WorkspaceFixture {
  protected ValueFactory myFactory;

  protected void setUp() throws Exception {
    super.setUp();
    myFactory = myBasis.ourValueFactory;
  }

  protected void tearDown() throws Exception {
    myFactory = null;
    super.tearDown();
  }

  public void testMarshallingUnsetValue() throws DatabaseInconsistentException {
    Object object = myFactory.marshall(ValueFactoryImpl.UNSET);
    assertNotNull(object);
    Value value = ((ValueFactoryImpl) myFactory).unmarshall(new ByteArrayInputStream((byte[]) object), null);
    assertEquals(ValueFactoryImpl.UNSET, value);
  }
}
