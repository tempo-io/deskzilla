package com.almworks.engine.items;

import com.almworks.items.api.*;
import org.jetbrains.annotations.*;

import static com.almworks.engine.items.DatabaseWrapperPrivateUtil.wrapWriter;

class WriteTransactionWrapper<T> implements WriteTransaction<T> {
  private final WriteTransaction<T> myTransaction;

  public WriteTransactionWrapper(@NotNull WriteTransaction<T> transaction) {
    //noinspection ConstantConditions
    if (transaction == null) throw new NullPointerException();
    myTransaction = transaction;
  }

  @Override
  public T transaction(DBWriter writer) throws DBOperationCancelledException {
    return myTransaction.transaction(wrapWriter(writer));
  }
}
