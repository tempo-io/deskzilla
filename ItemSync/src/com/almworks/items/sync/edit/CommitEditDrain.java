package com.almworks.items.sync.edit;

import com.almworks.integers.LongCollector;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.api.DBResult;
import com.almworks.items.sync.EditCommit;
import com.almworks.items.sync.EditorLock;
import com.almworks.items.util.AttributeMap;
import gnu.trove.TLongObjectHashMap;
import org.jetbrains.annotations.*;

class CommitEditDrain extends BaseEditDrain {
  private final EditCommit myCommit;
  private final boolean myReleaseAfterCommit;
  private final CommitCounterpart myCounterpart;
  private TLongObjectHashMap<AttributeMap> myBases;
  private final LongList myWereLocked;

  public CommitEditDrain(SyncManagerImpl manager, @NotNull CommitCounterpart counterpart, EditCommit commit,
    EditorLock lock, boolean releaseAfterCommit) {
    super(manager, lock);
    myCounterpart = counterpart;
    myCommit = commit;
    myReleaseAfterCommit = releaseAfterCommit;
    myWereLocked = lock != null ? lock.getItems() : LongList.EMPTY;
  }

  @Override
  protected void performTransaction() throws DBOperationCancelledException {
    myBases = myCounterpart.prepareCommit(getWriter());
    if (myBases == null) throw new DBOperationCancelledException();
    try {
      myCommit.performCommit(this);
    } finally {
      myBases = null;
    }
  }

  @Override
  protected void collectToMerge(LongCollector target) {
    super.collectToMerge(target);
    target.addAll(myWereLocked);
  }

  @Override
  protected void onTransactionFinished(DBResult<?> result) {
    boolean success = result.isSuccessful();
    if (myCounterpart != null) myCounterpart.commitFinished(myCommit, success, success && myReleaseAfterCommit);
    myCommit.onCommitFinished(success);
  }

  @NotNull
  @Override
  protected AttributeMap getBase(long item) {
    return myBases.get(item);
  }
}
