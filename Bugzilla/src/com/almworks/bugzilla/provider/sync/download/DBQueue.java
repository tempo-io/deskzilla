package com.almworks.bugzilla.provider.sync.download;

import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.bugzilla.provider.datalink.schema.MatchDownloadedBugs;
import com.almworks.bugzilla.provider.sync.BugBox;
import com.almworks.bugzilla.provider.sync.SyncUtil;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.api.DBResult;
import com.almworks.items.sync.*;
import org.almworks.util.Collections15;
import org.almworks.util.Log;

import java.util.*;

class DBQueue {
  private final BugzillaContext myContext;
  private final List<BugBox> myToWrite = Collections15.arrayList();
  private long myLastCommitICN = 0;
  private int myFailuresLeft = 5;
  private boolean myRunning = false;
  private int mySemiFinished = 0;

  public DBQueue(BugzillaContext context) {
    myContext = context;
  }

  public void addToUpdate(BugBox box) {
    addToUpdate(Collections.singleton(box));
  }

  public void addToUpdate(Collection<BugBox> boxes) {
    boolean enqueue;
    synchronized (myToWrite) {
      myToWrite.addAll(boxes);
      if (myToWrite.isEmpty()) return;
      enqueue = !myRunning;
      myRunning = true;
    }
    if (enqueue) enqueueUpdate();
  }

  public long getLastCommitICN() {
    synchronized (myToWrite) {
      return myLastCommitICN;
    }
  }

  public boolean waitFinished() throws InterruptedException {
    while (true) {
      synchronized (myToWrite) {
        if (myToWrite.isEmpty() && !myRunning && mySemiFinished == 0) return myFailuresLeft >= 0;
        myToWrite.wait(30);
      }
    }
  }

  private void enqueueUpdate() {
    myContext.getActor(SyncManager.ROLE).writeDownloaded(new DownloadProcedure<DBDrain>() {
      private final List<BugBox> myAttempt = Collections15.arrayList();

      @Override
      public void write(DBDrain drain) throws DBOperationCancelledException {
        try {
          while (true) {
            List<BugBox> task;
            synchronized (myToWrite) {
              assert myRunning;
              task = Collections15.arrayList(myToWrite);
              myAttempt.addAll(task);
              myToWrite.clear();
            }
            if (task.isEmpty()) break;
            updateDB(drain, task);
          }
        } finally {
          synchronized (myToWrite) {
            myRunning = false;
            mySemiFinished++;
          }
        }
      }

      private void updateDB(DBDrain drain, List<BugBox> task) {
        new MatchDownloadedBugs(myContext.getPrivateMetadata(), drain, task).perform();
        for (BugBox box : task) {
          long item = box.getItem();
          if (item <= 0) {
            Log.error("Missing item");
            continue;
          }
          ItemVersionCreator creator = drain.changeItem(item);
          SyncUtil.updateDB(box, creator, myContext);
        }
      }

      @Override
      public void onFinished(DBResult<?> result) {
        boolean successful = result.isSuccessful();
        long icn = result.getCommitIcn();
        try {
          if (!successful) {
            boolean retry;
            synchronized (myToWrite) {
              myFailuresLeft--;
              retry = myFailuresLeft >= 0;
            }
            if (retry) addToUpdate(myAttempt);
          }
          synchronized (myToWrite) {
            if (myRunning || myToWrite.isEmpty()) return;
          }
          addToUpdate(Collections.<BugBox>emptyList());
        } finally {
          synchronized (myToWrite) {
            mySemiFinished--;
            if (successful) myLastCommitICN = icn;
            myToWrite.notifyAll();
          }
        }
      }
    });
  }
}
