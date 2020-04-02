package com.almworks.bugzilla.provider;

import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.*;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.items.api.*;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.SyncAttributes;
import com.almworks.spi.provider.AbstractItemProblem;
import com.almworks.util.Pair;
import com.almworks.util.collections.Convertor;
import com.almworks.util.commons.Condition;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.properties.Role;
import com.almworks.util.threads.Bottleneck;
import org.almworks.util.*;
import org.jetbrains.annotations.*;
import org.picocontainer.Startable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class BugUploadQueue implements Startable {
  public static final Role<BugUploadQueue> ROLE = Role.role(BugUploadQueue.class.getName());

  private final Set<Long> myUploadQueue = Collections15.hashSet();

  private static final long WAIT_UNTIL_RETRY = 5 * 60 * 1000;
  private static final int PERIODICAL_RETRY_PERIOD = 60 * 1000;
  private long myLastUploadTime = 0;

  private final BugzillaContextImpl myContext;
  private final ComponentContainer mySubcontainer;
  private final Synchronizer mySynchronizer;

  private final Bottleneck myUploadingProcess = new Bottleneck(1000, ThreadGate.LONG(new Object()), new Runnable() {
    public void run() {
      synchronized (BugUploadQueue.this) {
        performUpload();
      }
    }
  });
  private final javax.swing.Timer myTimer = new javax.swing.Timer(PERIODICAL_RETRY_PERIOD, new ActionListener() {
    public void actionPerformed(ActionEvent e) {
      runUploadOnTime();
    }
  });

  public BugUploadQueue(BugzillaContextImpl context, ComponentContainer subcontainer) {
    myContext = context;
    mySubcontainer = subcontainer;
    mySynchronizer = mySubcontainer.requireActor(Engine.ROLE).getSynchronizer();
  }

  private void runUploadOnTime() {
    if (System.currentTimeMillis() >= myLastUploadTime + WAIT_UNTIL_RETRY)
      myUploadingProcess.request();
  }

  public void start() {
    ThreadGate.AWT.execute(new Runnable() {
      public void run() {
        myTimer.start();
      }
    });
  }

  public void stop() {
    ThreadGate.AWT.execute(new Runnable() {
      public void run() {
        myTimer.stop();
      }
    });
  }

  public void enqueue(long item) {
    synchronized (myUploadQueue) {
      myUploadQueue.add(item);
    }
    queueIncreased();
  }


  private void queueIncreased() {
    myUploadingProcess.request();
  }

  protected void performUpload() {
    myLastUploadTime = System.currentTimeMillis();

    Set<Long> bugs = getBugs();
    Pair<List<Long>, Set<Long>> pair = checkBugs(bugs);
    if (pair == null) {
      Log.warn("Upload not possible due to read transaction failed", new Throwable());
      return;
    }

    List<Long> deleted = pair.getFirst();
    if (!deleted.isEmpty()) {
      synchronized (myUploadQueue) {
        bugs.removeAll(deleted);
        myUploadQueue.removeAll(deleted);
      }
    }

    UploadBugTask task = new UploadBugTask(myContext, mySubcontainer, pair.getSecond(), afterSync(bugs));
    myContext.getConnection().subscribeToTaskUntilFinalState(task);
    task.startTask();
  }

  private Set<Long> getBugs() {
    synchronized (myUploadQueue) {
      int size = myUploadQueue.size();
      if (size == 0)
        return Collections15.emptySet();
      else
        return (Set<Long>) ((HashSet) myUploadQueue).clone();
    }
  }

  @Nullable
  private Pair<List<Long>, Set<Long>> checkBugs(final Set<Long> initial) {
    return Database.require().readForeground(new ReadTransaction<Pair<List<Long>, Set<Long>>>() {
      @Override
      public Pair<List<Long>, Set<Long>> transaction(DBReader reader) throws DBOperationCancelledException {
        final List<Long> deleted = Collections15.arrayList();
        final Set<Long> checked = Collections15.hashSet();

        for(final Long item : initial) {
          if (item == null || item < 0) continue;
          ItemVersion trunk = SyncUtils.readTrunk(reader, item);
          if(trunk.isInvisible()) {
            deleted.add(item);
          } else if(checkItem(trunk)) {
            checked.add(item);
          }
        }
        return Pair.create(deleted, checked);
      }
    }).waitForCompletion();
  }

  private boolean checkItem(ItemVersion trunk) {
    if (!trunk.equalValue(DBAttribute.TYPE, Bug.typeBug)) return false;
    Long conn = trunk.getValue(SyncAttributes.CONNECTION);
    return Util.equals(conn, myContext.getConnection().getConnectionItem());
  }

  private Runnable afterSync(final Set<Long> requestedBugs) {
    return requestedBugs == null || requestedBugs.isEmpty() ? Const.EMPTY_RUNNABLE : new Runnable() { public void run() {
      Collection<SyncProblem> problems = mySynchronizer.getProblems().copyCurrent();
      Set<Long> problemItems = Condition.<ItemSyncProblem>notNull().selectThenCollectSet(Convertor.<SyncProblem, ItemSyncProblem>downCastOrNull(ItemSyncProblem.class).lazyCollection(problems), AbstractItemProblem.TO_ITEM);
      synchronized (myUploadQueue) {
        requestedBugs.removeAll(problemItems);
        myUploadQueue.removeAll(requestedBugs);
      }
    }};
  }
}
