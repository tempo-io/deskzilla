package com.almworks.api.application.tree;

import com.almworks.items.api.*;
import com.almworks.util.commons.Procedure2;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.*;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.almworks.util.detach.DetachComposite;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.LinkedHashMap;
import java.util.Map;

public class ItemsPreviewManager {
  private final Object myLock = new Object();

  /**
   * Queue of nodes waiting for being recounted
   */
  private final LinkedHashMap<Object, Procedure2<Lifespan, DBReader>> myQueue = Collections15.linkedHashMap();

  /**
   * When nodes are grabbed for recount, we fill this map with detaches that are used to
   * signal cancellation of recount request.
   */
  private final Map<Object, DetachComposite> myDetachMap = Collections15.hashMap();

  private final Bottleneck myBottleneck =
    new Bottleneck(1000, ThreadGate.STRAIGHT, new Runnable() {
      public void run() {
        Database.require().readForeground(new ReadTransaction<Void>() {
          @Override
          public Void transaction(DBReader reader) throws DBOperationCancelledException {
            calculate(reader);
            return null;
          }
        });
      }
    });

  // manager has to be activated with setActive() initially
  private boolean myActive = false;

  public void setActive(boolean active) {
    synchronized(myLock) {
      if (active != myActive) {
        myActive = active;
        if (active) {
          // activation
          if (!myQueue.isEmpty()) {
            myBottleneck.requestDelayed();
          }
        } 
      }
    }
  }


  @ThreadAWT
  public void schedule(@NotNull Object jobKey, @Nullable Procedure2<Lifespan, DBReader> job, boolean cancelOngoing) {
    DetachComposite cancel;
    synchronized (myLock) {
      if (cancelOngoing) {
        cancel = myDetachMap.remove(jobKey);
        myQueue.remove(jobKey);
      } else {
        cancel = myDetachMap.get(jobKey);
        if (cancel != null && job != null) {
          // calculation is in progress, relax
          return;
        }
      }
      boolean added = job != null && (myQueue.put(jobKey, job) == null);
      if (added && myActive) {
        myBottleneck.request();
      }
    }
    if (cancel != null) {
      // already running - we have to cancel it
      cancel.detach();
    }
  }

  public boolean isJobEnqueued(@NotNull Object jobKey) {
    synchronized (myLock) {
      return myQueue.containsKey(jobKey) || myDetachMap.containsKey(jobKey);
    }
  }

  @ThreadAWT
  public void cancel(@NotNull Object jobKey) {
    DetachComposite cancel;
    synchronized (myLock) {
      cancel = myDetachMap.remove(jobKey);
      myQueue.remove(jobKey);
    }
    if (cancel != null) {
      cancel.detach();
    }
  }

  @CanBlock
  private void calculate(DBReader reader) {
    Object[] jobKeys;
    Procedure2<Lifespan, DBReader>[] jobs;
    DetachComposite[] detaches;
    synchronized (myLock) {
      if (!myActive)
        return;
      int size = myQueue.size();
      if (size == 0) return;
      jobKeys = myQueue.keySet().toArray(new Object[size]);
      jobs = myQueue.values().toArray(new Procedure2[size]);
      myQueue.clear();
      detaches = new DetachComposite[size];
      for (int i = 0; i < jobs.length; i++) {
        detaches[i] = new DetachComposite();
        Object jobKey = jobKeys[i];
        myDetachMap.put(jobKey, detaches[i]);
      }
    }
    for (int i = 0; i < jobs.length; i++) {
      Procedure2<Lifespan, DBReader> node = jobs[i];
      Lifespan lifespan = detaches[i];
      if (lifespan.isEnded())
        continue;
      try {
        node.invoke(lifespan, reader);
      } finally {
        try {
          DetachComposite detach;
          Object key = jobKeys[i];
          synchronized (myLock) {
            detach = myDetachMap.remove(key);
          }
          if (detach != null) {
            detach.detach();
          }
        } catch (Exception e) {
          Log.error(e);
        }
      }
    }
  }
}
