package com.almworks.database;

import com.almworks.api.database.UnsafeCollisionException;


/**
 * :todoc:
 *
 * @author sereda
 */
public class BasicOperationTests extends WorkspaceFixture {
  public void testEmptyWorkspace() {
    assertTrue(myWorkspace.getViews().getRootView() != null);
    assertTrue(myWorkspace.getViews().getUserView() != null);
  }

  public void testObjectCreation() throws UnsafeCollisionException {
    createObjectAndSetValue(myAttributeOne, "xxx");
  }

}
