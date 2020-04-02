package com.almworks.database;

import com.almworks.api.database.Transaction;
import com.almworks.api.install.TrackerProperties;
import com.almworks.util.Env;
import com.almworks.util.events.ProcessingLock;
import com.almworks.util.threads.ThreadSafe;
import org.almworks.util.ExceptionUtil;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class CommitLock extends ProcessingLock {
  public static final int DEFAULT_COMMIT_LOCK_TIMEOUT = 5000;

  private static final int TIMEOUT =
    Env.getInteger(TrackerProperties.COMMIT_LOCK_TIMEOUT, DEFAULT_COMMIT_LOCK_TIMEOUT);

  private final String myTransaction;
  private final AtomicLong myLastOperation = new AtomicLong(System.currentTimeMillis());

  public CommitLock(Transaction owner, Object lock) {
    super(owner, lock);
    myTransaction = String.valueOf(owner);
  }

  @ThreadSafe
  public void lock(@Nullable Object owner) {
    // mark before synchronized
    markTime();
    synchronized (myLock) {
      super.lock(owner);
      markTime();
    }
  }

  private void markTime() {
    myLastOperation.set(System.currentTimeMillis());
  }

  @ThreadSafe
  public void release(Object owner) {
    // mark before synchronized
    markTime();
    synchronized (myLock) {
      super.release(owner);
      markTime();
    }
  }

  public void waitRelease() throws InterruptedException {
    if (DEBUG) {
      debug("waiting " + myTransaction + " " + this);
    }
    synchronized (myLock) {
      while (true) {
        if (getLocked() <= 0) {
          // released
          return;
        }
        long now = System.currentTimeMillis();
        long last = myLastOperation.get();
        long until = last + TIMEOUT;
        if (until <= now) {
          // time to abandon
          abandon();
          return;
        }
        int wait = Math.max(1, Math.min((int) (until - now), TIMEOUT));
        super.waitRelease(wait);
      }
    }
  }

  private void abandon() {
    corrupt();
    Map<Object, Throwable> owners = getOwnersAndTraces();
    Log.warn(
      "ABANDONING COMMIT LOCK " +  this + ": timeout " + TIMEOUT + "ms, owners: " + owners.size());
    for (Object owner : owners.keySet()) {
      Log.warn("--- owner: " + owner);
      super.release(owner);
    }
    if (DEBUG) {
      assert Thread.holdsLock(myLock);
      Log.debug(" ================================= LOCK TRACES ==============================");
      for (Map.Entry<Object, Throwable> entry : owners.entrySet()) {
        Log.debug("Owner: " + entry.getKey());
        Log.debug("Trace:\n" + ExceptionUtil.getStacktrace(entry.getValue()));
        Log.debug("");
        Log.debug("+++++++++++++++++++++++++++++++");
      }
      debug("unlocked " + " <all> " + this);
    }
  }


  public String toString() {
    return super.toString() + "(" + myTransaction + ")";
  }
}
