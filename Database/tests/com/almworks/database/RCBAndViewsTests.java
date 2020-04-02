package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.util.exec.ThreadGate;

import java.util.Iterator;

/**
 * :todoc:
 *
 * @author sereda
 */
public class RCBAndViewsTests extends WorkspaceFixture {
  public static final int APPEARS = 1;
  public static final int CHANGES = 2;
  public static final int DISAPPEARS = 3;

  public static final int EXISTS = 0;
  private Tester myTest;

  protected void setUp() throws Exception {
    super.setUp();
    myTest = new Tester();
  }

  protected void tearDown() throws Exception {
    myTest = null;
    super.tearDown();
  }


  public void testDefaultAccessView() {
    Artifact artifact = createRCB("X");
    Revision revision = artifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN);
    getView().addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, myTest);
    myTest.test(EXISTS, artifact, revision);
    revision = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Y").asRevision();
    myTest.test(CHANGES, artifact, revision);
    revision = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "YYYYY").asRevision();
    myTest.test(CHANGES, artifact, revision);
    revision = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Z").asRevision();
    myTest.testUnchanged();
    artifact.getRCBExtension(true).closeLocalChain(myWorkspace);
    myTest.test(CHANGES, artifact, revision);
  }

  private ArtifactView getView() {
    ArtifactView view = myWorkspace.getViews().getUserView();
    view = view.filter(myWorkspace.getFilterManager().attributeSet(myAttributeOne, true));
    return view;
  }

  public void testMainChainAccessView() {
    Artifact artifact = createRCB("X");
    Revision revision = artifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN);
    ArtifactView view = getView().changeStrategy(RevisionAccess.ACCESS_MAINCHAIN);
    view.addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, myTest);
    myTest.test(EXISTS, artifact, revision);
    revision = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Y").asRevision();
    myTest.test(CHANGES, artifact, revision);
    revision = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "YYYYY").asRevision();
    myTest.testUnchanged();
    revision = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Z").asRevision();
    myTest.test(CHANGES, artifact, revision);
    artifact.getRCBExtension(true).closeLocalChain(myWorkspace);
    myTest.testUnchanged();
  }




  public void testDefaultAccessAndReincarnationMainChainOnly() {
    Artifact artifact = createRCB("X");
    getView().addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, myTest);
    Revision revision = artifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN);
    myTest.test(EXISTS, artifact, revision);
    revision = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Y").asRevision();
    myTest.test(CHANGES, artifact, revision);

    RCBArtifact rcb = artifact.getRCBExtension(true);
    Transaction transaction = createTransaction();
    RevisionCreator creator = rcb.startReincarnation(transaction);
    creator.setValue(myAttributeOne, "xx");
    transaction.commitUnsafe();
    myTest.testUnchanged();

    revision = changeArtifact(creator.getArtifact(), RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "yy").asRevision();
    myTest.testUnchanged();

    rcb.finishReincarnation(myWorkspace);
    myTest.test(CHANGES, artifact, revision);
  }

  public void testDefaultAccessAndReincarnationWithOpenBranch() {
    Artifact artifact = createRCB("X");
    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Y");
    changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "xx");
    final Revision revision = changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "zz").asRevision();
    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Z");

    getView().addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, myTest);
    myTest.test(EXISTS, artifact, revision);

    RCBArtifact rcb = artifact.getRCBExtension(true);
    RevisionCreator creator = reincarnate(rcb, "x1");
    myTest.testUnchanged();

    Revision linkRevision = changeArtifact(creator.getArtifact(), RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "y1").
      asRevision();
    myTest.testUnchanged();

    Iterator<RCBArtifact.Relink> ii = rcb.getReincarnationRequiredRelinks().iterator();
    while (ii.hasNext())
      ii.next().relink(myWorkspace, linkRevision);

    rcb.finishReincarnation(myWorkspace);
    myTest.test(CHANGES, artifact, revision);
  }

  public void testDefaultAccessAndReincarnationWithClosedBranch() {
    Artifact artifact = createRCB("X");
    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Y");
    changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "xx");
    changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "zz");
    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Z");
    Revision revision = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "zz").asRevision();

    RCBArtifact rcb = artifact.getRCBExtension(true);
    rcb.closeLocalChain(myWorkspace);

    getView().addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, myTest);
    myTest.test(EXISTS, artifact, revision);

    RevisionCreator creator = reincarnate(rcb, "X-Reinc");
    myTest.testUnchanged();

    Revision link1 = changeArtifact(creator.getArtifact(), RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "Y-Reinc").
      asRevision();
    myTest.testUnchanged();

    Revision link2 = changeArtifact(creator.getArtifact(), RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "ZZ-Reinc").
      asRevision();
    myTest.testUnchanged();

    Iterator<RCBArtifact.Relink> ii = rcb.getReincarnationRequiredRelinks().iterator();
    while (ii.hasNext()) {
      RCBArtifact.Relink relink = ii.next();
      String s = relink.getOldRevision().getValue(myAttributeOne, String.class);
      if (s.equals("Y"))
        relink.relink(myWorkspace, link1);
      else if (s.equals("zz"))
        relink.relink(myWorkspace, link2);
      else
        fail("bad s: " + s);
      myTest.testUnchanged();
    }

    rcb.finishReincarnation(myWorkspace);
    myTest.test(CHANGES, artifact, link2);
  }

  public void testMainChainAccessAndReincarnationWithMainChainOnly() {
    Artifact artifact = createRCB("X");
    Revision revision = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Y").asRevision();
    getView().changeStrategy(RevisionAccess.ACCESS_MAINCHAIN).addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, myTest);
    myTest.test(EXISTS, artifact, revision);

    RCBArtifact rcb = artifact.getRCBExtension(true);
    RevisionCreator creator = reincarnate(rcb, "xx");
    myTest.testUnchanged();

    revision = changeArtifact(creator.getArtifact(), RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "yy").asRevision();
    myTest.testUnchanged();

    rcb.finishReincarnation(myWorkspace);
    myTest.test(CHANGES, artifact, revision);
  }

  public void testMainChainAccessAndReincarnationWithOpenBranch() {
    Artifact artifact = createRCB("X");
    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Y");
    changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "xx");
    changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "zz");
    Revision revision = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Z").asRevision();

    getView().changeStrategy(RevisionAccess.ACCESS_MAINCHAIN).addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, myTest);
    myTest.test(EXISTS, artifact, revision);

    RCBArtifact rcb = artifact.getRCBExtension(true);
    RevisionCreator creator = reincarnate(rcb, "x1");
    myTest.testUnchanged();

    revision = changeArtifact(creator.getArtifact(), RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "y1").asRevision();
    myTest.testUnchanged();

    Iterator<RCBArtifact.Relink> ii = rcb.getReincarnationRequiredRelinks().iterator();
    while (ii.hasNext())
      ii.next().relink(myWorkspace, revision);

    rcb.finishReincarnation(myWorkspace);
    myTest.test(CHANGES, artifact, revision);
  }

  public void testMainChainAccessAndReincarnationWithClosedBranch() {
    Artifact artifact = createRCB("X");
    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Y");
    changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "xx");
    changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT, myAttributeTwo, "zz-two");
    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Z");
    Revision revision = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "zz").asRevision();

    RCBArtifact rcb = artifact.getRCBExtension(true);
    rcb.closeLocalChain(myWorkspace);

    getView().changeStrategy(RevisionAccess.ACCESS_MAINCHAIN).addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, myTest);
    myTest.test(EXISTS, artifact, revision);

    RevisionCreator creator = reincarnate(rcb, "X-Reinc");
    myTest.testUnchanged();

    Revision link1 = changeArtifact(creator.getArtifact(), RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "Y-Reinc").
      asRevision();
    myTest.testUnchanged();

    Revision link2 = changeArtifact(creator.getArtifact(), RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "ZZ-Reinc").
      asRevision();
    myTest.testUnchanged();

    Iterator<RCBArtifact.Relink> ii = rcb.getReincarnationRequiredRelinks().iterator();
    while (ii.hasNext()) {
      RCBArtifact.Relink relink = ii.next();
      String s = relink.getOldRevision().getValue(myAttributeOne, String.class);
      if (s.equals("Y"))
        relink.relink(myWorkspace, link1);
      else if (s.equals("zz"))
        relink.relink(myWorkspace, link2);
      else
        fail("bad s: " + s);
      myTest.testUnchanged();
    }

    rcb.finishReincarnation(myWorkspace);
    myTest.test(CHANGES, artifact, link2);
    Revision last = artifact.getLastRevision();
    assertEquals("zz-two", last.getValue(myAttributeTwo, String.class));
  }


  public void testHasLocalChangesView() {
    Artifact artifact = createRCB("X");
    Artifact artifact2 = createRCB("XXX");
    Revision revision = changeArtifact(artifact2, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "SCSCSC").asRevision();

    Filter filter = myWorkspace.getFilterManager().isLocalChange();
    ArtifactView view = myWorkspace.getViews().getUserView().filter(filter);
    view.addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, myTest);
    myTest.test(EXISTS, artifact2, revision);

    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Y");
    myTest.testUnchanged();

    revision = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "Y-1").asRevision();
    myTest.test(APPEARS, artifact, revision);

    revision = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "Y-2").asRevision();
    myTest.test(CHANGES, artifact, revision);

    artifact.getRCBExtension(true).closeLocalChain(myWorkspace);
    myTest.test(DISAPPEARS, artifact, revision);
  }


  private class Tester extends ArtifactListener.Adapter {
    private Artifact myArtifact = null;
    private Revision myRevision = null;
    private boolean myKnown = false;
    private int myCode = 0;

    public boolean onArtifactAppears(Artifact artifact, Revision lastRevision) {
      return note(artifact, lastRevision, APPEARS);
    }

    public boolean onArtifactChanges(Artifact artifact, Revision prevRevision, Revision newRevision) {
      return note(artifact, newRevision, CHANGES);
    }

    public boolean onArtifactDisappears(Artifact artifact, Revision lastSeenRevision, Revision unseenRevision) {
      return note(artifact, lastSeenRevision, DISAPPEARS);
    }

    public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
      return note(artifact, lastRevision, EXISTS);
    }

    public void test(int code, Artifact artifact, Revision revision) {
      assertTrue(myKnown);
      assertEquals(code, myCode);
      assertEquals(artifact, myArtifact);
      assertEquals(revision, myRevision);
      myKnown = false;
    }

    public void testUnchanged() {
      assertFalse(myKnown);
    }

    private boolean note(Artifact artifact, Revision revision, int code) {
      assert !myKnown;
      myKnown = true;
      myArtifact = artifact;
      myRevision = revision;
      myCode = code;
      return true;
    }
  }
}
