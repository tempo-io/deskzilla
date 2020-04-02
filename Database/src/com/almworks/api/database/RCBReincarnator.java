package com.almworks.api.database;

import java.util.SortedSet;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface RCBReincarnator {
  int getIncarnation();

  /**
   * Returns chain of a previous incarnation, or null.
   */ 
  RevisionChain getBuriedChain(int incarnation);

  /**
   * @return creator for the first revision of a new chain. Until call to finishReincarnation(),
   * this chain is not visible.
   * @param transaction
   */
  RevisionCreator startReincarnation(Transaction transaction);

  SortedSet<Relink> getReincarnationRequiredRelinks();

  void finishReincarnation(Transaction transaction);

  /**
   * finishes reincarnation in a separate transaction
   */
  void finishReincarnation(TransactionControl transactionControl);

  /**
   * Tests if revision is on the reincarnation chain.
   */
  boolean isReincarnating(Revision revision);

  boolean isReincarnating();

  RevisionCreator getReincarnationCreator();

  void cancelReincarnation();

  long getLastIncarnationUcn();


  interface Relink {
    Revision getOldRevision();

    WCN getOldLinkWCN();

    void relink(Transaction transaction, Revision newRevision);

    /**
     * Performs relink in a separate transaction
     */
    void relink(TransactionControl transactionControl, Revision newRevision);

    boolean isBranchEnd();
  }
}
