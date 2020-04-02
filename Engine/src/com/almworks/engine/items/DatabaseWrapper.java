package com.almworks.engine.items;

import com.almworks.api.misc.WorkArea;
import com.almworks.items.DatabaseDelegator;
import com.almworks.items.api.*;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.detach.Lifespan;
import org.picocontainer.Startable;

import java.io.IOException;
import java.io.PrintStream;

import static com.almworks.engine.items.ItemStorageAdaptor.wrapExpr;

/**
 * Note: this class does <b>not</b> extend DatabaseDelegator so that if someone added more abstract methods to Database, compilation would break <i>here</i> to remind that these methods should possibly be wrapped.
 * */
class DatabaseWrapper extends Database implements Startable {
  private final DatabaseDelegator myDelegator;

  public DatabaseWrapper(WorkArea workArea) {
    myDelegator = new DatabaseDelegator(workArea);
  }

  @Override
  public void start() {
    myDelegator.start();
  }

  @Override
  public void stop() {
    myDelegator.stop();
  }

  @Override
  public <T> DBResult<T> read(DBPriority priority, ReadTransaction<T> transaction) {
    ReadTransactionWrapper<T> wrappedTransaction = new ReadTransactionWrapper<T>(transaction);
    return new DBResultWrapper<T>(myDelegator.read(priority, wrappedTransaction));
  }

  @Override
  public <T> DBResult<T> write(DBPriority priority, WriteTransaction<T> transaction) {
    WriteTransactionWrapper<T> wrappedTransaction = new WriteTransactionWrapper<T>(transaction);
    return new DBResultWrapper<T>(myDelegator.write(priority, wrappedTransaction));
  }

  @Override
  public DBLiveQuery liveQuery(Lifespan lifespan, BoolExpr<DP> expr, DBLiveQuery.Listener listener) {
    BoolExpr<DP> wrappedExpr = wrapExpr(expr);
    DBLiveQueryListenerWrapper wrappedListener = new DBLiveQueryListenerWrapper(listener);
    return new DBLiveQueryWrapper(myDelegator.liveQuery(lifespan, wrappedExpr, wrappedListener));
  }

  DBLiveQuery liveQueryUnwrapped(Lifespan lifespan, BoolExpr<DP> expr, DBLiveQuery.Listener listener) {
    return myDelegator.liveQuery(lifespan, expr, listener);
  }

  @Override
  public void addListener(Lifespan lifespan, DBListener listener) {
    DBListenerWrapper wrappedListener = new DBListenerWrapper(listener);
    myDelegator.addListener(lifespan, wrappedListener);
  }

  void addListenerUnwrapped(Lifespan lifespan, DBListener listener) {
    myDelegator.addListener(lifespan, listener);
  }

  @Override
  public void setLongHousekeepingAllowed(boolean housekeepingAllowed) {
    myDelegator.setLongHousekeepingAllowed(housekeepingAllowed);
  }

  @Override
  public void dump(PrintStream writer) throws IOException {
    myDelegator.dump(writer);
  }

  @Override
  public void registerTrigger(DBTrigger trigger) {
    DBTriggerWrapper wrappedTrigger = new DBTriggerWrapper(trigger);
    myDelegator.registerTrigger(wrappedTrigger);
  }

  void registerTriggerUnwrapped(DBTrigger trigger) {
    myDelegator.registerTrigger(trigger);
  }

  @Override
  public boolean isDbThread() {
    return myDelegator.isDbThread();
  }
}
