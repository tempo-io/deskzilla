package com.almworks.engine.items;

import com.almworks.items.api.*;
import org.jetbrains.annotations.*;

import static com.almworks.engine.items.DatabaseWrapperPrivateUtil.wrapReader;

class ReadTransactionWrapper<T> implements ReadTransaction<T> {
  private final ReadTransaction<T> myTransaction;

  public ReadTransactionWrapper(@NotNull ReadTransaction<T> transaction) {
    //noinspection ConstantConditions
    if (transaction == null) throw new NullPointerException();
    myTransaction = transaction;
  }

  @Override
  public T transaction(DBReader reader) throws DBOperationCancelledException {
    return myTransaction.transaction(wrapReader(reader));
  }
}
