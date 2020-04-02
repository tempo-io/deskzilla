package com.almworks.database;

import com.almworks.api.universe.Expansion;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface InternalTransactionListener {
  void onInternalAfterCommit(Expansion underlying);

  void onInternalBeforeCommit(Expansion underlying);
}
