package com.almworks.api.database;

import com.almworks.api.database.typed.TypedArtifact;
import org.jetbrains.annotations.*;

public interface Artifact extends ArtifactPointer, Aspected {
  Artifact[] EMPTY_ARRAY = new Artifact[0];

  /**
   * Returns physical or logical chain.
   */
  RevisionChain getChain(RevisionAccess strategy);

  Revision getFirstRevision(); // todo: remove?;

  long getKey();

  Revision getLastRevision(WCN wcn, RevisionAccess strategy);

  Revision getLastRevision(RevisionAccess strategy);

  Revision getLastRevision(WCN wcn);

  /**
   * Equals getLastRevision(WCN.LATEST) - special ability to access uncommitted revisions of new objects
   */
  Revision getLastRevision();

  <T extends TypedArtifact> T getTyped(Class<T> typedClass);

  @Nullable
  RCBArtifact getRCBExtension(boolean assumeRcb);

  boolean hasRCBExtension();

  WCN getWCN();

  boolean isAccessStrategySupported(RevisionAccess strategy);

  boolean containsRevision(Revision revision);

  /**
   * Returns false if artifact cannot be used
   */
  boolean isValid();

  /**
   * Checks if the artifact can be used.
   * <p>
   * Returns true if the artifact has been committed to the database (it has at least the first committed revision).
   * If transaction argument is specified,
   * also returns true if the artifact has not yet been created, but is changed in the transaction t (so you
   * can use it within the same transaction).
   */
  boolean isAccessibleIn(Transaction transaction);
}
