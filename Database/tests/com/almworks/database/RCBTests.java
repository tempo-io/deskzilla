package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.database.objects.remote.RCBLocalChain;
import com.almworks.database.objects.remote.RCBMainChain;

import java.io.IOException;
import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public class RCBTests extends WorkspaceFixture {
  public void testCreateMultiChain() {
    Artifact artifact = createRCB("one");
    Revision revision = artifact.getLastRevision();
    assertEquals(revision, artifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN));
    assertEquals(revision, artifact.getLastRevision(RevisionAccess.ACCESS_LOCAL));
  }

  public void testRightChains() {
    Artifact artifact = createRCB("one");
    doTestChain(artifact.getChain(RevisionAccess.ACCESS_LOCAL), RevisionAccess.ACCESS_LOCAL);
    doTestChain(artifact.getChain(RevisionAccess.ACCESS_MAINCHAIN), RevisionAccess.ACCESS_MAINCHAIN);
    doTestChain(artifact.getChain(RevisionAccess.ACCESS_DEFAULT), RevisionAccess.ACCESS_LOCAL);
  }

  private void doTestChain(RevisionChain chain, RevisionAccess access) {
    Class checkClass = access == RevisionAccess.ACCESS_LOCAL ?
      (Class) RCBLocalChain.class : RCBMainChain.class;
    assertEquals(checkClass, chain.getClass());
    RevisionChain chain2 = chain.getLastRevision().getChain();
    assertEquals(checkClass, chain2.getClass());
    assertEquals(chain, chain2);
    RevisionChain chain3 = chain.getFirstRevision().getChain();
    assertEquals(checkClass, chain3.getClass());
    assertEquals(chain, chain3);
  }

  public void testCreatorKeepsChain() {
    Artifact artifact = createRCB("one");
    Transaction t = myWorkspace.beginTransaction();
    RevisionCreator creator = t.changeArtifact(artifact);
    creator.setValue(myAttributeOne, "xx");
    t.commitUnsafe();
    Revision lastRevision = artifact.getLastRevision();
    assertEquals(lastRevision, creator.asRevision());
    assertEquals(lastRevision, creator.asRevision().getChain().getLastRevision());
  }

  public void testDifferentAccess() throws IOException, InterruptedException {
    Artifact artifact = createRCB("one");
    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "two");

    Revision lastMainChain = artifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN);
    Revision lastLocal = artifact.getLastRevision(RevisionAccess.ACCESS_LOCAL);
    assertEquals("one", lastMainChain.getValue(myAttributeOne, String.class));
    assertEquals("two", lastLocal.getValue(myAttributeOne, String.class));
  }

  public void testDifferentAccessReversed() {
    Artifact artifact = createRCB(null);
    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "two");
    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "one");
    // we have conflict?
    Revision lastMainChain = artifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN);
    Revision lastLocal = artifact.getLastRevision(RevisionAccess.ACCESS_LOCAL);
    assertEquals("one", lastMainChain.getValue(myAttributeOne, String.class));
    assertEquals("two", lastLocal.getValue(myAttributeOne, String.class));
    Revision dummyRevision = lastLocal.getPrevRevision();
    assertNull(dummyRevision.getValue(myAttributeOne));
  }

  public void testLocalChainEnds() {
    Artifact artifact = createRCB("one");
    assertEquals("one", artifact.getLastRevision().getValue(myAttributeOne, String.class));

    setObjectValue(artifact, myAttributeTwo, "x");
    assertEquals("x", artifact.getLastRevision().getValue(myAttributeTwo, String.class));

    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "two");
    assertEquals("two", artifact.getLastRevision().getValue(myAttributeOne, String.class));

    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "two"); // merge
    assertEquals("two", artifact.getLastRevision().getValue(myAttributeOne, String.class));

    artifact.getRCBExtension(true).closeLocalChain(myWorkspace);
    assertEquals("two", artifact.getLastRevision().getValue(myAttributeOne, String.class));

    Revision rev = artifact.getLastRevision();
    assertEquals("x", rev.getValue(myAttributeTwo, String.class));
    assertEquals("two", rev.getValue(myAttributeOne, String.class));
    rev = rev.getPrevRevision();
    assertEquals("x", rev.getValue(myAttributeTwo, String.class));
    assertEquals("two", rev.getValue(myAttributeOne, String.class));
    rev = rev.getPrevRevision();
    assertEquals("x", rev.getValue(myAttributeTwo, String.class));
    assertEquals("one", rev.getValue(myAttributeOne, String.class));
    rev = rev.getPrevRevision();
    assertEquals("one", rev.getValue(myAttributeOne, String.class));
    assertNull(rev.getValue(myAttributeTwo, String.class));
    rev = rev.getPrevRevision();
    assertNull(rev);
  }

  public void testPrivateChanges() {
    // myAttributeTwo is not "understood" by "bugzilla"
    Artifact artifact = createRCB("one");
    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "wawa");
    // bugzilla does not create any new revisions, so it is linked to the same revision
    Transaction t = myWorkspace.beginTransaction();
    artifact.getRCBExtension(true).closeLocalChain(t);
    t.commitUnsafe();

    Revision rev = artifact.getLastRevision();
    assertEquals("one", rev.getValue(myAttributeOne, String.class));
    assertEquals("wawa", rev.getValue(myAttributeTwo, String.class));
    rev = rev.getPrevRevision();
    assertEquals("one", rev.getValue(myAttributeOne, String.class));
    assertEquals("wawa", rev.getValue(myAttributeTwo, String.class));
    rev = rev.getPrevRevision();
    assertEquals("one", rev.getValue(myAttributeOne, String.class));
    assertNull(rev.getValue(myAttributeTwo));
    rev = rev.getPrevRevision();
    assertNull(rev);

    rev = artifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN);
    assertEquals("one", rev.getValue(myAttributeOne, String.class));
    assertNull(rev.getValue(myAttributeTwo));
    rev = rev.getPrevRevision();
    assertNull(rev);
  }

  public void testCreatorRevisionInterator() {
    Artifact artifact = createRCB("one");
    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "two");
    RevisionCreator creator = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "wawa");
    Revision revision = creator.asRevision();
    assertEquals("wawa", revision.getValue(myAttributeOne, String.class));
    revision = revision.getPrevRevision();
    assertEquals("two", revision.getValue(myAttributeOne, String.class));
    revision = revision.getPrevRevision();
    assertEquals("one", revision.getValue(myAttributeOne, String.class));
    assertNull(revision.getPrevRevision());
  }

  public void testEtherealCreatorRevisionInterator() {
    Artifact artifact = createRCB("one");
    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "two");
    Transaction t = createTransaction();
    RevisionCreator creator = t.changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL);
    creator.setValue(myAttributeOne, "wawa");
    // NO UPLOAD
    Revision revision = creator.asRevision();
    assertEquals("wawa", revision.getValue(myAttributeOne, String.class));
    revision = revision.getPrevRevision();
    assertEquals("two", revision.getValue(myAttributeOne, String.class));
    revision = revision.getPrevRevision();
    assertEquals("one", revision.getValue(myAttributeOne, String.class));
    assertNull(revision.getPrevRevision());
  }

  public void testGetLocalChangesWithRevision() {
    Artifact artifact = createRCB("one");
    RCBArtifact rcb = artifact.getRCBExtension(true);

    Revision revision = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "two").asRevision();
    Map<ArtifactPointer, Value> localChanges = rcb.getLocalChanges(revision);
    assertEquals(1, localChanges.size());
    assertEquals("two", localChanges.values().iterator().next().getValue(String.class));

    assertEquals(0, rcb.getLocalChanges(artifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN)).size());

    rcb.closeLocalChain(myWorkspace);

    // this should remain after closing
    localChanges = rcb.getLocalChanges(revision);
    assertEquals(1, localChanges.size());
    assertEquals("two", localChanges.values().iterator().next().getValue(String.class));

    assertEquals(0, rcb.getLocalChanges(artifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN)).size());
    assertEquals(0, rcb.getLocalChanges(artifact.getLastRevision(RevisionAccess.ACCESS_LOCAL)).size());
  }
}
