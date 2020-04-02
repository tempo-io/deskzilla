package com.almworks.api.database;

import com.almworks.util.commons.Procedure2;

import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface RCBBranching {
  /**
   * Closes last open local chain, marking it as "equals" to the last
   * revision on main chain.
   */
  void closeLocalChain(Transaction transaction);

  void closeLocalChain(Transaction transaction, Procedure2<RevisionCreator, Transaction> additionalClosure);

  WCN closeLocalChain(TransactionControl transactionControl);

  /**
   * Returns all changes in the open local branch. If there's no changes or no open local branch,
   * returns empty map.
   */
  Map<ArtifactPointer, Value> getLocalChanges();

  /**
   * Returns all changes in the open local branch from start to the specified revision.
   * If revision does not belong to the last open local branch, returns empty map.
   */
  Map<ArtifactPointer, Value> getLocalChanges(Revision revision);

  boolean isLocalRevision(Revision revision);

  /**
   * @return true if:
   * 1. there's an open local chain;
   * 2. there is a revision in the main (remote) chain after last "merge point".
   */
  boolean hasConflict();

  /**
   * When !hasConflict(), behavior is undefined.
   * Returns the base revision object for each branch. (DEFAULT/LOCAL and REMOTE.) Base objects are
   * considered to be synchronized.
   */
  Revision getConflictBase(RevisionAccess strategy);

  /**
   * When in conflict, returns all changes from remote or local branch that need to be merged. This is
   * a union of all changes since conflict base.
   */
  Map<ArtifactPointer, Value> getConflictChanges(RevisionAccess strategy);

  /**
   * Marks existing (or ethereal) revision in local chain as merged from this main chain revision,
   * effectively making both a conflict base for future conflict.
   */
  void markMerged(Transaction transaction, Revision remoteSource, Revision localResult);

  void markMerged(TransactionControl transactionControl, Revision remoteSource, Revision localResult);

  boolean hasOpenLocalBranch();

  Revision getLocalChainStartingRevision(Revision localRevision);
}
