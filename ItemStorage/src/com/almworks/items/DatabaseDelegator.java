package com.almworks.items;

import com.almworks.api.misc.WorkArea;
import com.almworks.items.api.*;
import com.almworks.items.impl.DBConfiguration;
import com.almworks.items.impl.SQLiteDatabase;
import com.almworks.util.Env;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.Log;
import org.almworks.util.RuntimeInterruptedException;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;
import org.picocontainer.Startable;

import java.io.*;
import java.util.concurrent.atomic.AtomicReference;

public class DatabaseDelegator extends Database implements Startable {
  public static final String DATABASE_FILE_NAME = "items.db";

  private static final long START_WAIT_TIMEOUT = 120000;
  private static final Object START_UP = new Object();
  private final WorkArea myWorkArea;
  private final AtomicReference<Object> myDB = new AtomicReference<Object>(null);

  public DatabaseDelegator(WorkArea workArea) {
    myWorkArea = workArea;
  }

  @Override
  public void start() {
    db();
  }

  private SQLiteDatabase doStart() {
    File databaseFile = new File(myWorkArea.getRootDir(), DATABASE_FILE_NAME);
    DBConfiguration configuration = DBConfiguration.createDefault(databaseFile);
    File tempDir = Env.isWindows() ? myWorkArea.getTempDir() : null;
    SQLiteDatabase db = new SQLiteDatabase(databaseFile, tempDir, configuration);
    db.start();
    db.setLongHousekeepingAllowed(true);
    return db;
  }

  @Override
  public void stop() {
    Object obj = myDB.get();
    if (!(obj instanceof SQLiteDatabase)) return;
    SQLiteDatabase db = (SQLiteDatabase) obj;
    myDB.compareAndSet(db, null);
    try {
      db.stop();
    } catch (Throwable  e) {
      Log.error("cannot stop database", e);
      if (e instanceof ThreadDeath)
        throw (ThreadDeath) e;
    }
  }

  @NotNull
  private SQLiteDatabase db() {
    long start = System.currentTimeMillis();
    int attempt = 0;
    while (true) {
      Object obj = myDB.get();
      if (obj instanceof SQLiteDatabase) return (SQLiteDatabase) obj;
      attempt++;
      if (attempt > 3 && (System.currentTimeMillis() - start) > START_WAIT_TIMEOUT) break;
      if (myDB.compareAndSet(null, START_UP)) {
        boolean success = false;
        try {
          myDB.set(doStart());
          success = true;
        } finally {
          if (!success) {
            myDB.compareAndSet(START_UP, null);
            Log.error("Failed to start DB");
          }
        }
      } else
        try {
          Thread.sleep(30);
        } catch (InterruptedException e) {
          throw new RuntimeInterruptedException(e);
        }
    }
    throw new IllegalStateException("database has not started successfully");
  }

  @Override
  public <T> DBResult<T> read(DBPriority priority, ReadTransaction<T> transaction) {
    return db().read(priority, transaction);
  }

  @Override
  public <T> DBResult<T> write(DBPriority priority, WriteTransaction<T> transaction) {
    return db().write(priority, transaction);
  }

  @Override
  public void addListener(Lifespan lifespan, DBListener listener) {
    db().addListener(lifespan, listener);
  }

  @Override
  public void dump(PrintStream writer) throws IOException {
    db().dump(writer);
  }

  @Override
  public void setLongHousekeepingAllowed(boolean housekeepingAllowed) {
    db().setLongHousekeepingAllowed(housekeepingAllowed);
  }

  @Override
  public void registerTrigger(DBTrigger trigger) {
    db().registerTrigger(trigger);
  }

  @Override
  public boolean isDbThread() {
    return db().isDbThread();
  }

  @Override
  public DBLiveQuery liveQuery(Lifespan lifespan, BoolExpr<DP> expr, DBLiveQuery.Listener listener) {
    return db().liveQuery(lifespan, expr, listener);
  }
}
