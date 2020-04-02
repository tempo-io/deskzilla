package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.util.events.ProcessingLock;
import com.almworks.util.exec.ThreadGate;

import java.util.Set;
import java.util.logging.Level;

public class TransactionControllerTests extends WorkspaceFixture {
  public TransactionControllerTests() {
    super(CommitLock.DEFAULT_COMMIT_LOCK_TIMEOUT * 6);
  }

  public void testAbandoningCommitLock() {
    // tests that a "forgotten" commit lock would be abandoned after timeout so other transactions may proceed
    Artifact A = createObjectAndSetValue(myAttributeOne, "1");

    myWorkspace.addListener(ThreadGate.NEW_THREAD, new TransactionListener.Adapter() {
      public void onCommit(Transaction transaction, Set<Artifact> affectedArtifacts, ProcessingLock commitLock) {
        commitLock.lock("forever");
        // lock and do not unlock
      }
    });

    allowDebugOutput(Level.INFO);



    // This would succeed, but a lock would remain
    changeArtifact(A, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "2");

    // This would block forever if not for lock-abandoning mechanism! The test will time out
    changeArtifact(A, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "3");

    assertEquals("3", A.getLastRevision().getValue(myAttributeOne, String.class));
  }
}
