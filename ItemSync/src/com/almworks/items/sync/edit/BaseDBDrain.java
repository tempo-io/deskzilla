package com.almworks.items.sync.edit;

import com.almworks.integers.LongCollector;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.sync.*;
import com.almworks.items.sync.impl.*;
import com.almworks.util.collections.LongSet;
import com.almworks.util.commons.Procedure;
import com.almworks.util.exec.ThreadGate;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.*;

public abstract class BaseDBDrain extends BasicVersionSource implements DBDrain, WriteTransaction<Object>, Procedure<DBResult<?>> {
  private final SyncManagerImpl myManager;
  private final Branch myBranch;
  private final LongSet myToMerge = new LongSet();
  private DBWriter myWriter;

  public BaseDBDrain(SyncManagerImpl manager, Branch branch) {
    myManager = manager;
    myBranch = branch;
  }

  @Override
  public Object transaction(DBWriter writer) throws DBOperationCancelledException {
    if (myWriter != null) {
      Log.error("Called twice");
      return null;
    }
    myWriter = writer;
    performTransaction();
    LongSet toMerge = new LongSet();
    collectToMerge(toMerge);
    myWriter = null;
    myManager.autoMergeNowPartial(writer, toMerge, null);
    return null;
  }

  protected void collectToMerge(LongCollector target) {
    target.addAll(myToMerge);
  }

  @Override
  public final void invoke(DBResult<?> result) {
    transactionFinished(result);
  }

  private void transactionFinished(DBResult<?> result) {
    onTransactionFinished(result);
    List<Throwable> errors = result.getErrors();
    if (errors != null && !errors.isEmpty()) {
      for (Throwable error : errors) {
        if (!(error instanceof DBOperationCancelledException)) {
          Log.error("Commit error", error);
        }
      }
    }
  }

  protected abstract void onTransactionFinished(DBResult<?> result);

  @Override
  public ItemVersionCreator changeItem(DBIdentifiedObject obj) {
    return changeItem(materialize(obj));
  }

  @Override
  public ItemVersionCreator changeItem(ItemProxy proxy) {
    long item = materialize(proxy);
    if (item > 0) return changeItem(item);
    return BranchUtil.instance(getWriter()).dummyCreator(item, this);
  }

  @Override
  public List<ItemVersionCreator> changeItems(LongList items) {
    if (items == null || items.isEmpty()) return Collections.emptyList();
    final long[] array = items.toNativeArray();
    return new AbstractList<ItemVersionCreator>() {
      @Override
      public ItemVersionCreator get(int index) {
        return changeItem(array[index]);
      }

      @Override
      public int size() {
        return array.length;
      }
    };
  }

  @Override
  public void finallyDo(ThreadGate gate, Procedure<Boolean> procedure) {
    getWriter().finallyDo(gate, procedure);
  }

  @Override
  @NotNull
  public DBReader getReader() {
    return getWriter();
  }

  @Override
  public ItemVersionCreator createItem() {
    return BranchUtil.instance(getWriter()).newItem(this);
  }

  @Override
  public ItemVersionCreator changeItem(long item) {
    return BranchUtil.instance(getWriter()).write(item, this);
  }

  @Override
  public long materialize(ItemProxy object) {
    return object != null ? object.findOrCreate(this) : 0;
  }

  @Override
  public long materialize(DBIdentifiedObject object) {
    return object != null ? getWriter().materialize(object) : 0;
  }

  protected abstract void performTransaction() throws DBOperationCancelledException;

  public SyncManagerImpl getManager() {
    return myManager;
  }

  @NotNull
  protected DBWriter getWriter() {
    assert myWriter != null;
    return myWriter;
  }

  public DBResult<Object> start() {
    return myManager.enquireWrite(this).finallyDoWithResult(ThreadGate.STRAIGHT, this);
  }

  @NotNull
  @Override
  public ItemVersion forItem(DBIdentifiedObject object) {
    return forItem(materialize(object));
  }

  public Branch getBranch() {
    return myBranch;
  }

  @NotNull
  public ItemVersion forItem(long item) {
    return BranchUtil.instance(getReader()).readItem(item, getBranch());
  }

  public void beforeShadowableChanged(long item, boolean isNew) {
    myToMerge.add(item);
  }
}
