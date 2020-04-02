package com.almworks.database;

import com.almworks.api.database.*;

import java.util.SortedSet;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ReincarnationTests extends WorkspaceFixture {
  public void testReincarnationWithoutLocalChains() {
    Artifact artifact = createXYZ();
    RCBArtifact rcb = artifact.getRCBExtension(true);

    RevisionCreator creator = reincarnate(rcb, "xxxxyy");
    Artifact reincarnation = creator.getArtifact();

    changeArtifact(reincarnation, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "yyzzzz");

    SortedSet<RCBArtifact.Relink> set = rcb.getReincarnationRequiredRelinks();
    assertEquals(0, set.size());

    rcb.finishReincarnation(myWorkspace);

    Revision revision = artifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN);
    assertEquals("yyzzzz", revision.getValue(myAttributeOne, String.class));
    revision = revision.getPrevRevision();
    assertEquals("xxxxyy", revision.getValue(myAttributeOne, String.class));
    assertNull(revision.getPrevRevision());

    revision = artifact.getLastRevision(RevisionAccess.ACCESS_LOCAL);
    assertEquals("yyzzzz", revision.getValue(myAttributeOne, String.class));
    revision = revision.getPrevRevision();
    assertEquals("xxxxyy", revision.getValue(myAttributeOne, String.class));
    assertNull(revision.getPrevRevision());
  }

  private Artifact createXYZ() {
    Artifact artifact = createRCB("xxxx");
    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "yyyy");
    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "zzzz");
    return artifact;
  }

  public void testReincarnationWithOpenLocalChain() {
    Artifact artifact = createXYZ();
    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "A");
    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "B");

    RCBArtifact rcb = artifact.getRCBExtension(true);

    RevisionCreator creator = reincarnate(rcb, "qqqq");
    Artifact reinc = creator.getArtifact();
    Revision linkPoint = changeArtifact(reinc, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "zzzz").asRevision();
    changeArtifact(reinc, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "pppp");

    SortedSet<RCBArtifact.Relink> relinks = rcb.getReincarnationRequiredRelinks();
    assertEquals(1, relinks.size());
    RCBArtifact.Relink relink = relinks.iterator().next();
    assertEquals("zzzz", relink.getOldRevision().getValue(myAttributeOne, String.class));

    relink.relink(myWorkspace, linkPoint);
    rcb.finishReincarnation(myWorkspace);

    Revision rev = artifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN);
    assertEquals("pppp", rev.getValue(myAttributeOne, String.class));
    rev = rev.getPrevRevision();
    assertEquals("zzzz", rev.getValue(myAttributeOne, String.class));
    rev = rev.getPrevRevision();
    assertEquals("qqqq", rev.getValue(myAttributeOne, String.class));
    assertNull(rev.getPrevRevision());

    rev = artifact.getLastRevision(RevisionAccess.ACCESS_LOCAL);
    assertEquals("A", rev.getValue(myAttributeOne, String.class));
    assertEquals("B", rev.getValue(myAttributeTwo, String.class));
    rev = rev.getPrevRevision();
    assertEquals("A", rev.getValue(myAttributeOne, String.class));
    assertNull(rev.getValue(myAttributeTwo, String.class));
    rev = rev.getPrevRevision();
    assertEquals("zzzz", rev.getValue(myAttributeOne, String.class));
    rev = rev.getPrevRevision();
    assertEquals("qqqq", rev.getValue(myAttributeOne, String.class));
    assertNull(rev.getPrevRevision());

    rcb.closeLocalChain(myWorkspace);

    rev = artifact.getLastRevision(RevisionAccess.ACCESS_DEFAULT);
    assertEquals("pppp", rev.getValue(myAttributeOne, String.class));
    assertEquals("B", rev.getValue(myAttributeTwo, String.class));
    rev = rev.getPrevRevision().getPrevRevision();
    assertEquals("A", rev.getValue(myAttributeOne, String.class));
    assertEquals("B", rev.getValue(myAttributeTwo, String.class));
  }
}
