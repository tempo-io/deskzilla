package com.almworks.database;

/**
 * :todoc:
 *
 * @author sereda
 */
public class AuxiliaryMethodsTests extends WorkspaceFixture {
  public void testToString() {
    String s = myAttributeOne.toString();
    assertTrue(s.indexOf("one") >= 0);

    // now checking that toString() does not throw exception
    unsetObjectValue(myAttributeOne, myAttributeOne.attributeName());
    s = myAttributeOne.toString();

    // attribute name is taken from first revision
    assertFalse("something is wrong with the test case", s.indexOf("one") >= 0);
  }
}
