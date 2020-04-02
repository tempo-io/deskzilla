package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.universe.Atom;
import com.almworks.util.events.ProcessingLock;
import com.almworks.util.properties.PropertyMap;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface TransactionControlExt extends TransactionControl {
  TransactionListener getListenersSnapshot();

  Object getGlobalLock();

  /**
   * Returns general-purpose map that is used by transactions as a global storage.
   */
  PropertyMap getTransactionServiceMap();

  /**
   * Tells the transaction controller that notifications for this WCN has been executed.
   */
  void onLateWCNPromotion(WCN commitWCN);

  WCN getLateWCN();

  void setLateWCN(WCN earlyWCN);

  void callCommittedAtomsHook(TransactionExt transaction, Atom[] atoms);

  void addCommittedAtomsHook(CommittedAtomsHook hook);

  /**
   * Asks transaction controller to provide a refernce count to the pending commit.
   * This object may be passed around (moved from thread to thread) to allow commit-order-dependent
   * classes such as ArtifactView implementors to lock out commits until they are done processing
   * messages.
   * <p>
   * This method blocks until previously acquired commit reference count is still held.
   *
   * @return commit reference with <b>initial count set to 1</b>
   * @throws InterruptedException
   */
  ProcessingLock beginCommit(Transaction owner) throws InterruptedException;

  void onTransactionFinished();

  long getLastTransactionTime();
  
  interface CommittedAtomsHook {
    void onCommittedAtoms(TransactionExt transaction, Atom[] atoms);
  }
}
