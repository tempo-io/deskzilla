package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.util.WorkspaceUtils;

import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public class MergeTests extends WorkspaceFixture {
  // todo: merge & reincarnation, merge & closing

  public void testSimpleConflict() {
    Artifact artifact = createRCB("X");
    RCBBranching rcb = artifact.getRCBExtension(true);
    assertNull(rcb.getConflictBase(RevisionAccess.ACCESS_LOCAL));
    assertNull(rcb.getConflictBase(RevisionAccess.ACCESS_MAINCHAIN));
    assertFalse(rcb.hasConflict());

    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "X1");
    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "Y1");

    assertFalse(rcb.hasConflict());
/*
    assertNull(rcb.getConflictBase(RevisionAccess.ACCESS_LOCAL));
    assertNull(rcb.getConflictBase(RevisionAccess.ACCESS_MAINCHAIN));
*/

    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeTwo, "Y");
    assertTrue(rcb.hasConflict());

    Revision remoteBase = rcb.getConflictBase(RevisionAccess.ACCESS_MAINCHAIN);
    assertEquals("X", remoteBase.getValue(myAttributeOne, String.class));
    assertNull(remoteBase.getValue(myAttributeTwo, String.class));

    Revision localBase = rcb.getConflictBase(RevisionAccess.ACCESS_LOCAL);
    assertEquals("X", remoteBase.getValue(myAttributeOne, String.class));
    assertNull(remoteBase.getValue(myAttributeTwo, String.class));

    Map<ArtifactPointer, Value> map = WorkspaceUtils.diff(remoteBase, localBase);
    assertEquals(0, map.size());

    Map<ArtifactPointer, Value> localChanges = rcb.getConflictChanges(RevisionAccess.ACCESS_LOCAL);
    assertEquals(2, localChanges.size());
    assertEquals("X1", localChanges.get(myAttributeOne).getValue(String.class));
    assertEquals("Y1", localChanges.get(myAttributeTwo).getValue(String.class));

    Map<ArtifactPointer, Value> remoteChanges = rcb.getConflictChanges(RevisionAccess.ACCESS_MAINCHAIN);
    assertEquals(1, remoteChanges.size());
    assertEquals("Y", remoteChanges.get(myAttributeTwo).getValue(String.class));
  }

  public void testSimpleMerge() {
    Artifact artifact = createRCB("X");
    RCBBranching rcb = artifact.getRCBExtension(true);

    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "L-Y");

    Revision remote = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "R-X").asRevision();
    assertTrue(rcb.hasConflict());

    // make hand-merge, create new local revision
    Revision local = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "R-X").asRevision();
    rcb.markMerged(myWorkspace, remote, local);

    assertFalse(rcb.hasConflict());
    assertEquals(local, rcb.getConflictBase(RevisionAccess.ACCESS_LOCAL));
    assertEquals(remote, rcb.getConflictBase(RevisionAccess.ACCESS_MAINCHAIN));
    assertEquals(0, rcb.getConflictChanges(RevisionAccess.ACCESS_LOCAL).size());
    assertEquals(0, rcb.getConflictChanges(RevisionAccess.ACCESS_MAINCHAIN).size());
  }

  public void testSecondMerge() {
    Artifact artifact = createRCB("X");
    RCBBranching rcb = artifact.getRCBExtension(true);

    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "L-Y");
    Revision remote = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "R-X").asRevision();
    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "R-X");
    Revision local = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "R-Y").asRevision();
    rcb.markMerged(myWorkspace, remote, local);

    // local change
    unsetObjectValue(artifact, myAttributeTwo);
    assertFalse(rcb.hasConflict());

    Revision remote2 = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeTwo, "R-Y").asRevision();
    assertTrue(rcb.hasConflict());
    assertEquals(local, rcb.getConflictBase(RevisionAccess.ACCESS_LOCAL));
    assertEquals(remote, rcb.getConflictBase(RevisionAccess.ACCESS_MAINCHAIN));
    Map<ArtifactPointer, Value> localMap = rcb.getConflictChanges(RevisionAccess.ACCESS_LOCAL);
    assertEquals(1, localMap.size());
    assertTrue(localMap.containsKey(myAttributeTwo));
    assertNull(localMap.get(myAttributeTwo));
    Map<ArtifactPointer, Value> remoteMap = rcb.getConflictChanges(RevisionAccess.ACCESS_MAINCHAIN);
    assertEquals(1, remoteMap.size());
    assertEquals("R-Y", remoteMap.get(myAttributeTwo).getValue(String.class));

    local = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "R-Y").asRevision();
    rcb.markMerged(myWorkspace, remote2, local);
    assertFalse(rcb.hasConflict());
  }
}
