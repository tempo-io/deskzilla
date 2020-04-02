package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.universe.Expansion;
import com.almworks.util.events.ProcessingLock;
import com.almworks.util.tests.CollectionsCompare;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;
import util.concurrent.CountDown;

import java.util.List;
import java.util.Set;

/**
 * :todoc:
 *
 * @author sereda
 */
public class TransactionTests extends WorkspaceFixture {
  protected void setUp() throws Exception {
    super.setUp();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testEmptyTransactionDoesNotAffectUniverse() {
    long oldID = getLastAtomID();
    Transaction transaction = myWorkspace.beginTransaction();
    RevisionCreator creator = transaction.changeArtifact(myAttributeOne);
    String oldValue = creator.asRevision().getValue(SystemObjects.ATTRIBUTE.NAME);
    creator.setValue(SystemObjects.ATTRIBUTE.NAME, "hacka");
    creator.setValue(SystemObjects.ATTRIBUTE.NAME, oldValue);
    transaction.changeArtifact(myAttributeTwo);
    transaction.commitUnsafe();
    long newID = getLastAtomID();
    assertEquals(oldID, newID);
  }

  public void testOnCommitOrder() throws InterruptedException {
    final Transaction t1 = createTransaction();
    final Transaction t2 = createTransaction();
    t1.getEventSource().addStraightListener(Lifespan.FOREVER, new TransactionListener.Adapter() {
      public void onAfterUnderlyingCommit(Expansion underlying, boolean success) {
        sleep(400);
      }
    });
    t2.getEventSource().addStraightListener(Lifespan.FOREVER, new TransactionListener.Adapter() {
      public void onBeforeUnderlyingCommit(Expansion underlying) {
        sleep(200);
      }
    });
    RevisionCreator a1 = t1.createArtifact();
    RevisionCreator a2 = t2.createArtifact();
    Thread r1 = new Thread("r1") {
      public void run() {
        t1.commitUnsafe();
      }
    };
    Thread r2 = new Thread("r2") {
      public void run() {
        t2.commitUnsafe();
      }
    };
    final List<Transaction> list = Collections15.arrayList();
    final CountDown count = new CountDown(2);
    myWorkspace.addListener(new TransactionListener.Adapter() {
      public void onCommit(Transaction transaction, Set<Artifact> affectedArtifacts, ProcessingLock lock) {
        list.add(transaction);
        count.release();
      }
    });
    r1.start();
    r2.start();
    count.acquire();
    new CollectionsCompare().order(new Transaction[] {t1, t2}, list);
  }

  public void testChangeArtifactWithDifferentStrategiesProhibited() {
    Artifact artifact = createRCB("x");
    Transaction t = createTransaction();
    RevisionCreator creator1 = t.changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT);

    // this is ok: LOCAL = DEFAULT
    RevisionCreator creator2 = t.changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL);

    try {
      // this is not ok:
      RevisionCreator creator3 = t.changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN);
      fail("successfully changing an artifact in one transaction in different ways");
    } catch (Exception e) {
      // ok
    }

    // same vice versa
    artifact = createRCB("y");
    t = createTransaction();
    creator1 = t.changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN);
    try {
      t.changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL);
      fail("successfully changing an artifact in one transaction in different ways");
    } catch (Exception e) {
      // ok
    }
    try {
      t.changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT);
      fail("successfully changing an artifact in one transaction in different ways");
    } catch (Exception e) {
      // ok
    }
  }

  public void testChangesFromABaseRevisionOfNormalArtifact() {
    Artifact artifact = createObjectAndSetValue(myAttributeOne, "X");
    Revision r1 = artifact.getLastRevision();
    Revision r2 = changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT, myAttributeTwo, "Y").asRevision();

    // ok
    createTransaction().changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT, r2);

    // not ok - cannot change local artifact from not last revision - doesn't support branching
    try {
      createTransaction().changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT, r1);
      fail("changed local artifact with base non-last revision");
    } catch (Exception e) {
      // ok
    }
  }

  public void testChangesFromABaseRevisionOfRCBArtifact() {
    final Artifact artifact = createRCB("X");
    RCBArtifact rcb = artifact.getRCBExtension(true);

    final Revision R1 = artifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN);
    Revision R2 = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Y").asRevision();
    Revision R3 = changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "Z").asRevision();

    // cannot change artifact's main chain from a base revision
    try {
      createTransaction().changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, R1);
      fail("changed rcb artifact's main chain from an earlier base revision");
    } catch (Exception e) {
      // ok
    }

    // this is ok
    createTransaction().changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, R3);

    // CHANGING LOCAL CHAIN - TRICKY
    Transaction t = createTransaction();
    RevisionCreator creator = t.changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, R1);
    creator.setValue(myAttributeTwo, "a");
    t.commitUnsafe();
    Revision L1 = creator.asRevision();
    Revision L2 = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "b").asRevision();

    // changing local chain is supported only from the last local revision
    // this is ok:
    createTransaction().changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, L2);

    // not ok:
    try {
      createTransaction().changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, R3);
      // fail("successfully changed local chain based on remote revision while having an open local chain"); // todo rethink
    } catch (Exception e) {
      // ok
    }
    try {
      createTransaction().changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, L1);
      // fail("successfully changed local chain based on not-last local revision"); // todo rethink
    } catch (Exception e) {
      // ok
    }

    Revision L3 = changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "c").asRevision();
    rcb.closeLocalChain(myWorkspace);

    // now we have closed chain:
    //
    //   R3:Z <----- L3:c
    //     |           |
    //   R2:Y        L2:b
    //     |           |
    //   R1:X <----- L1:a
    //

    // we can change local and remote chains from a last remote revision
    createTransaction().changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, R3);
    createTransaction().changeArtifact(artifact, RevisionAccess.ACCESS_MAINCHAIN, R3);

    // we cannot change local revision from any other revision
    final Revision[] ra = {R1, R2, L1, L2, L3};
    for (int i = 0; i < ra.length; i++) {
      final int index = i;
      mustThrow("failed: " + i, new RunnableE() {
        public void run() {
          createTransaction().changeArtifact(artifact, RevisionAccess.ACCESS_LOCAL, ra[index]);
        }
      });
    }

    // test values
    assertEquals("Z", artifact.getLastRevision(RevisionAccess.ACCESS_LOCAL).getValue(myAttributeOne, String.class));
    assertEquals("c", artifact.getLastRevision(RevisionAccess.ACCESS_LOCAL).getValue(myAttributeTwo, String.class));

    assertEquals("X", L3.getValue(myAttributeOne, String.class));
    assertEquals("c", L3.getValue(myAttributeTwo, String.class));

    assertEquals("X", L1.getValue(myAttributeOne, String.class));
    assertEquals("a", L1.getValue(myAttributeTwo, String.class));

    assertEquals("Y", R2.getValue(myAttributeOne, String.class));
    assertEquals(null, R2.getValue(myAttributeTwo, String.class));
  }

  private long getLastAtomID() {
    return myUniverse.getGlobalIndex().last().getAtomID();
  }
}
