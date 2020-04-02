package com.almworks.database;

import com.almworks.api.database.Revision;
import com.almworks.api.database.Transaction;
import com.almworks.api.universe.Atom;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface TransactionExt extends Transaction {
  Atom createAtom(boolean forceCreation);

  void forceNotification(Revision revision);
}
