package com.almworks.api.database;

import com.almworks.api.universe.Expansion;
import com.almworks.util.events.ProcessingLock;

import java.util.Collection;
import java.util.Set;


/**
 * Listener class for Transaction and TransactionControl. All methods except onCommit may be called in
 * a random order; onCommit is called in order of transaction commit.
 *
 * @author sereda
 */
public interface TransactionListener {
  void onBeforeUnderlyingCommit(Expansion underlying);

  void onAfterUnderlyingCommit(Expansion underlying, boolean success);

  void onNewRevisionsAppeared(Transaction transaction, Collection<Revision> newRevisions);

  /**
   * Called after transaction becomes COMMITTED. This method is guaranteed to be called in order
   * of transaction WCN. 
   */
  void onCommit(Transaction transaction, Set<Artifact> affectedArtifacts, ProcessingLock commitLock);

  public static abstract class Adapter implements TransactionListener {
    public void onBeforeUnderlyingCommit(Expansion underlying) {
    }

    public void onAfterUnderlyingCommit(Expansion underlying, boolean success) {
    }

    public void onNewRevisionsAppeared(Transaction transaction, Collection<Revision> newRevisions) {
    }

    public void onCommit(Transaction transaction, Set<Artifact> affectedArtifacts, ProcessingLock commitLock) {
    }
  }
}
