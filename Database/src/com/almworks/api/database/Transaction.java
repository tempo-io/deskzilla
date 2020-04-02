package com.almworks.api.database;

import com.almworks.api.database.typed.TypedArtifact;
import com.almworks.database.objects.remote.IllegalBaseRevisionException;
import com.almworks.util.events.EventSource;


/**
 * :todoc:
 *
 * @author sereda
 */
public interface Transaction {
  EventSource<TransactionListener> getEventSource();

  /**
   * Changes artifact, starting from baseRevision. If artifact and access strategy support
   * branching, and base revision is not the last, a branch is created.
   */ 
  RevisionCreator changeArtifact(ArtifactPointer artifact, RevisionAccess accessStrategy, Revision baseRevision) throws
    IllegalBaseRevisionException;

  RevisionCreator changeArtifact(ArtifactPointer artifact, RevisionAccess accessStrategy);

  RevisionCreator changeArtifact(ArtifactPointer artifact);

  /**
   * If beforeCommit fails with either of declared exceptions, transaction is not
   * wasted. You can change all properties and you can call assumeLastVersion
   * on :todo:
   */
  void commitUnsafe() throws UnsafeCollisionException;

  void commit() throws CollisionException;

  WCN getCommitWCN();

  RevisionCreator[] getPendingChanges();

  boolean isChanging(ArtifactPointer artifact);

  boolean isCommitted();

  /**
   * Returns true if no changes are performed within a working transaction.
   */
  boolean isEmpty();

  boolean isRolledBack();

  boolean isWorking();

  <T extends TypedArtifact> RevisionCreator createArtifact(Class<T> typedClass);

  RevisionCreator createArtifact(ArtifactFeature[] features);

  /**
   * Same as newObject(Artifact.DEFAULT_FEATURES)
   */
  RevisionCreator createArtifact();

  void rollback();

  void verifyViewNotChanged(ArtifactView view);

  RevisionCreator getPendingCreator(Transaction transaction, Filter.Equals filter);
}
