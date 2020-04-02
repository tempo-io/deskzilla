package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.database.schema.Schema;
import com.almworks.util.events.ProcessingLock;
import com.almworks.util.exec.ThreadGate;

import java.util.*;

public class RCBRegressionTests extends WorkspaceFixture {
  public void testTransactionNotificationDoesNotIncludeArtifactOnReincarnation() {
    final Artifact artifact = createRCB("x");
    final boolean[] found = {false};
    myWorkspace.addListener(new TransactionListener.Adapter() {
      public void onCommit(Transaction transaction, Set<Artifact> affectedArtifacts, ProcessingLock lock) {
        for (Artifact affectedArtifact : affectedArtifacts) {
          if (affectedArtifact.equals(artifact))
            found[0] = true;
        }
      }
    });

    RCBArtifact rcb = artifact.getRCBExtension(true);
    Transaction t = createTransaction();
    RevisionCreator creator = rcb.startReincarnation(t);
    creator.setValue(myAttributeOne, "y");
    commitTransaction(t);

    rcb.finishReincarnation(myWorkspace);

    assertTrue(found[0]);
  }

  public void testLocalChainRescanning() {
    Artifact artifact = createRCB("X");
    Revision revision1 = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "XX-1").asRevision();
    Revision revision2 = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "XX-2").asRevision();
    assertEquals(revision1.getChain(), revision2.getChain());
    assertEquals(revision1, revision2.getPrevRevision());
  }

  public void testTakingWrongLastLocalChain() {
    Artifact artifact = createRCB("X");
    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "X-L-1");
    changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "X-1");
    RCBArtifact rcb = artifact.getRCBExtension(true);
    rcb.closeLocalChain(myWorkspace);
    Revision ch1 = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "X-L-2").asRevision();
    Revision ch2 = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "X-L-3").asRevision();

    long head1 = myUniverse.getAtom(myBasis.getAtomID(ch1)).getLong(Schema.KA_CHAIN_HEAD);
    long head2 = myUniverse.getAtom(myBasis.getAtomID(ch2)).getLong(Schema.KA_CHAIN_HEAD);
    assertEquals(head1, head2);
  }

  public void testClosingSecondChain() {
    Artifact artifact = createRCB("X");
    RCBArtifact rcb = artifact.getRCBExtension(true);
    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "X-L-1");
    rcb.closeLocalChain(myWorkspace);
    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "X-L-2");
    rcb.closeLocalChain(myWorkspace);
  }

  public void testLocalChangesNegation() {
    // if i change locally a value, and then change it back, local changes should be empty.
    Artifact artifact = createRCB("XXX");
    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "YYY");
    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "XXX");
    RCBArtifact rcb = artifact.getRCBExtension(true);
    assertEquals(0, rcb.getLocalChanges().size());
  }

  public void testInvalidatingMainChainAfterClosingLocalChain() {
    // main chain value cache should be invalidated after closing local chain
    Artifact artifact = createRCB("XXX");

    String value = artifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN).getValue(myAttributeTwo, String.class);
    assertNull(value);
    // now it is cached

    changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "YYY");
    RCBArtifact rcb = artifact.getRCBExtension(true);
    rcb.closeLocalChain(myWorkspace);

    // value should be invalidated and rescanned
    value = artifact.getLastRevision(RevisionAccess.ACCESS_LOCAL).getValue(myAttributeTwo, String.class);
    assertEquals("YYY", value);
  }



  public void testCloseLocalBranchKeepsCurrentServerData() {
    Artifact a = createRCB("M:1:X");
    RCBArtifact rcb = a.getRCBExtension(true);

    changeArtifact(a, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "L:1:XXX");

    Transaction t = createTransaction();
    RevisionCreator creator = rcb.startReincarnation(t);
    creator.setValue(myAttributeOne, "M:1:RR:X");
    creator.setValue(myAttributeTwo, "M:2:RR:Y");
    t.commitUnsafe();
    Revision first = creator.asRevision();
    changeArtifact(creator.getArtifact(), RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "M:1:RR:XX");

    SortedSet<RCBReincarnator.Relink> relinks = rcb.getReincarnationRequiredRelinks();
    assertEquals(1, relinks.size());
    for (Iterator<RCBReincarnator.Relink> ii = relinks.iterator(); ii.hasNext();) {
      RCBReincarnator.Relink relink = ii.next();
      relink.relink(myWorkspace, first);
    }

    rcb.finishReincarnation(myWorkspace);

    // close local chain that has been created on the first incarnation.
    // it will connect to the last revision of new main chain
    // if bug manifests, it will not see myAttributeTwo because it does not contain a copy of it in the beginning
    // of the chain
    rcb.closeLocalChain(myWorkspace);

    Revision last = a.getLastRevision(RevisionAccess.ACCESS_LOCAL);
    String two = last.getValue(myAttributeTwo, String.class);
    assertEquals("M:2:RR:Y", two);
  }

  public void testCloseLocalBranchDoesNotCreateTwoRevisions() {
    // well, in fact it does create two revisions in the local branch - one is "closure" that finishes the physical
    // local chain, and the other is the existing last revision in the main chain.
    // as a result, filtering view listeners do not correctly handle events
    Artifact a = createRCB("1");
    RCBArtifact rcb = a.getRCBExtension(true);
    Revision localRev = changeArtifact(a, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "2").asRevision();
    Revision mainRev = changeArtifact(a, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "11").asRevision();

    ArtifactView localChanges = myWorkspace.getViews().getUserView().filter(myWorkspace.getFilterManager().isLocalChange());
    assertEquals(1, localChanges.count());
    TestListener listener = new TestListener();
    localChanges.addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, listener);
    listener.clear();

    rcb.closeLocalChain(myWorkspace);
    listener.assertValues(null, null, a, null);
/*
    Revision r = a.getLastRevision(RevisionAccess.ACCESS_LOCAL);
    assertEquals(mainRev, r);
    r = r.getPrevRevision();
    assertEquals(localRev, r);
*/
  }

  /**
   * see #965
   */
  public void testStartingLocalBranchFiresAppearsOnLocalChangeView() {
    ArtifactView view = myWorkspace.getViews().getUserView().filter(myWorkspace.getFilterManager().isLocalChange());
    Artifact a = createRCB("1");
    TestListener test = new TestListener();
    view.addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, test);
    assertEquals(0, view.count());
    Revision r = changeArtifact(a, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "2").asRevision();
    test.assertValues(null, r.getArtifact(), null, null);
    assertEquals(1, view.count());
  }

  /**
   * see #1146
   */
  public void testReincarnationAfterClosureDoesNotCreateNewClosure() {
    Artifact A = createRCB("X");
    RCBArtifact rcb = A.getRCBExtension(true);
    changeArtifact(A, RevisionAccess.ACCESS_MAINCHAIN, myAttributeTwo, "Y");

    // local changes
    changeArtifact(A, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "Z");
    changeArtifact(A, RevisionAccess.ACCESS_MAINCHAIN, myAttributeTwo, "Z");
    rcb.closeLocalChain(myWorkspace);
    // now we have "closure" that copies all untouched values from the main change

    // surprise - the reincarnation
    Transaction t = createTransaction();
    RevisionCreator creator = rcb.startReincarnation(t);
    creator.setValue(myAttributeOne, "P");
    creator.setValue(myAttributeTwo, "Z");
    t.commitUnsafe();

    // somehow it's important that the new main chain contains more than 1 revision
    t = createTransaction();
    creator = t.changeArtifact(creator.getArtifact());
    creator.setValue(SystemObjects.ATTRIBUTE.NAME, "xxx");
    t.commitUnsafe();

    SortedSet<RCBReincarnator.Relink> relinks = rcb.getReincarnationRequiredRelinks();
    assertEquals(2, relinks.size());
    for (RCBReincarnator.Relink relink : relinks) {
      relink.relink(myWorkspace, creator.asRevision());
    }

    rcb.finishReincarnation(myWorkspace);

    // now we should have attributeOne equal to "P" because at local chain we didn't touch it
    // but if there is an error, we get "X".
    assertEquals("P", A.getLastRevision().getValue(myAttributeOne, String.class));
  }

  // this just works :(
  // cannot catch #1358
  public void testUpdateLostWhenGrandparentViewUpdatesInMainChain() {
    for (int i = 0; i < 3; i++) {
      Transaction t = createTransaction();
      RevisionCreator creator = t.createArtifact(ArtifactFeature.REMOTE_ARTIFACT);
      creator.setValue(myAttributeOne, "X1");
      creator.setValue(myAttributeTwo, "X2");
      t.commitUnsafe();
      Artifact a = creator.getArtifact();

      FilterManager fm = myWorkspace.getFilterManager();

      ArtifactView allView = myWorkspace.getViews().getUserView().changeStrategy(RevisionAccess.ACCESS_LOCAL);
      ArtifactView grandParent = allView.filter(fm.attributeEquals(myAttributeOne, "X1", true));
      ArtifactView parent = grandParent.filter(fm.attributeSet(myAttributeTwo, true));
      ArtifactView subject = parent.filter(fm.attributeEquals(myAttributeTwo, "X2", true));

      TestListener listener = new TestListener(5000);
//      subject.addListenerFuture(ThreadGate.STRAIGHT, listener);
      subject.addListener(ThreadGate.NEW_THREAD, WCN.ETERNITY, listener);

      sleep(2000);
      listener.clear();

      //changeArtifact(a, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Y1");
      RCBArtifact rcb = a.getRCBExtension(true);
      Transaction transaction = createTransaction();
      creator = rcb.startReincarnation(transaction);
      creator.setValue(myAttributeOne, "Y1");
      creator.setValue(myAttributeTwo, "X2");
      transaction.commitUnsafe();
      assertEquals(0, rcb.getReincarnationRequiredRelinks().size());
      rcb.finishReincarnation(myWorkspace);

      listener.assertValues(null, null, a, null);
    }
  }
}
