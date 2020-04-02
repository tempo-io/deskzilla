package com.almworks.api.database;

import com.almworks.util.Pair;
import org.jetbrains.annotations.*;

public class WCNCollector {
  private long myMaximumUcn;

  public void update(@Nullable Pair<?, WCN> whatever) {
    if (whatever != null) {
      update(whatever.getSecond());
    }
  }

  public void update(@Nullable WCN wcn) {
    if (wcn != null) {
      myMaximumUcn = Math.max(myMaximumUcn, wcn.getUCN());
    }
  }

  public void update(Transaction transaction) {
    if (transaction != null && transaction.isCommitted()) {
      update(transaction.getCommitWCN());
    }
  }

  @Nullable
  public WCN getWcn() {
    return myMaximumUcn == 0 ? null : WCN.createWCN(myMaximumUcn);
  }
}
