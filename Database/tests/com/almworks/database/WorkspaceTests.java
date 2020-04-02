package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.util.WorkspaceUtils;

import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public class WorkspaceTests extends WorkspaceFixture {
  public void testDoubleRepair() {
    myWorkspace.close();
    myBasis = new Basis(myUniverse, ConsistencyWrapper.FAKE);
    myBasis.start();
    myWorkspace = new WorkspaceImpl(myBasis);
    myWorkspace.repair();
  }

  public void testDiffContainsNull() {
    Artifact a1 = createObjectAndSetValue(myAttributeOne, "one");
    Artifact a2 = createObjectAndSetValue(myAttributeTwo, "two");
    Map<ArtifactPointer, Value> map = WorkspaceUtils.diff(a1.getLastRevision(), a2.getLastRevision());
    assertEquals(2, map.size());
    assertNull(map.get(myAttributeOne));
    assertEquals("two", map.get(myAttributeTwo).getValue(String.class));
  }
}
