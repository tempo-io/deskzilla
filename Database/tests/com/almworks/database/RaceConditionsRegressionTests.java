package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.universe.Expansion;

public class RaceConditionsRegressionTests extends WorkspaceFixture {
  /**
   * The problem: when transaction is performed, UCN gets promoted first, then notifications are sent
   * and RevisionMonitor updates chains. If I access last revision in the middle, I'll make previous
   * revision cached for the new UCN.
   *
   * #660
   */
  public void testArtifactChainCachingBadLastRevision() throws InterruptedException {
    if (IGNORE_OLD_DATABASE_TESTS_KNOWN_TO_SOMETIMES_FAIL)
      return;
    
    Artifact artifact = createObjectAndSetValue(myAttributeOne, "X");
    final Transaction t = createTransaction();

    Revision r1 = artifact.getLastRevision();

    RevisionCreator creator = t.changeArtifact(artifact);
    creator.setValue(myAttributeTwo, "Y");
    Revision r2 = creator.asRevision();

    new Thread() {
      public void run() {
        t.getEventSource().addStraightListener(new TransactionListener.Adapter() {
          public void onAfterUnderlyingCommit(Expansion underlying, boolean success) {
            // revision monitor listens onNewRevisionsAppeared(), which happens after this message.
            // let him wait
            try {
              Thread.sleep(1500);
            } catch (InterruptedException e) {
            }
          }
        });
        t.commitUnsafe();
      }
    }.start();

    Revision lastRevision = null;
    long finish = System.currentTimeMillis() + 3000;
    do {
      lastRevision = artifact.getLastRevision();
      if (r2.equals(lastRevision))
        break;
      Thread.sleep(50);
    } while (System.currentTimeMillis() < finish);
    assertEquals(r2, lastRevision);
  }
}
