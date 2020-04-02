package com.almworks.api.database;

import com.almworks.util.Pair;
import com.almworks.util.exec.ThreadGate;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.Lifespan;


/**
 * :todoc:
 *
 * @author sereda
 */
public interface TransactionControl {
  Transaction beginTransaction();

  Pair<Detach,WCN> addListener(TransactionListener listener);

  WCN addListener(Lifespan lifespan, TransactionListener listener);

  void removeListener(TransactionListener listener);

  Pair<Detach,WCN> addListener(ThreadGate gate, TransactionListener listener);

  WCN addListener(Lifespan lifespan, ThreadGate gate, TransactionListener listener);
}
