package com.almworks.timetrack.gui.bigtime;

import com.almworks.api.application.LoadedItem;
import com.almworks.items.api.*;
import com.almworks.timetrack.api.*;
import com.almworks.timetrack.gui.TrackerSimplifier;
import com.almworks.timetrack.impl.TaskTiming;
import com.almworks.timetrack.impl.TimeTrackingUtil;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.commons.Procedure;
import com.almworks.util.datetime.DateUtil;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.ThreadGate;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifecycle;
import org.jetbrains.annotations.*;

import java.util.List;

/**
 * Base implementation for {@link TotalTimeSpent} and {@link TimeSpentByMe}
 * {@link BigTime}s.
 */
public abstract class TimeSpentBigTimeImpl extends BigTimeImpl {
  private final Lifecycle myLifecycle = new Lifecycle();
  private TimeTrackerTask myCurrTask;
  private volatile int mySpent;

  public TimeSpentBigTimeImpl(String name, String description, String id) {
    super(name, description, id);
  }

  public void getBigTimeText(final Procedure<String> proc) {
    final TrackerSimplifier ts = new TrackerSimplifier(Context.require(TimeTracker.TIME_TRACKER));
    if(ts.task == null) {
      proc.invoke(EMPTY_VALUE);
      return;
    }

    if(!ts.task.equals(myCurrTask)) {
      loadCommittedTime(ts, proc);
    } else {
      reportTotalTime(ts, proc);
    }
  }

  private void loadCommittedTime(final TrackerSimplifier ts, final Procedure<String> proc) {
    myCurrTask = ts.task;
    final TimeTrackingCustomizer customizer = Context.require(TimeTrackingCustomizer.ROLE);
    Database.require().readForeground(new ReadTransaction<Integer>() {
      @Override
      public Integer transaction(DBReader reader) throws DBOperationCancelledException {
        final LoadedItem item = ts.task.load(reader);
        if(item != null) {
          return getStoredValue(customizer, item);
        }
        return null;
      }
    }).finallyDo(ThreadGate.AWT, new Procedure<Integer>() {
      @Override
      public void invoke(Integer arg) {
        mySpent = Util.NN(arg, 0);
        reportTotalTime(ts, proc);
      }
    });
  }

  /**
   * @param customizer The {@link TimeTrackingCustomizer} instance.
   * @param item The artifact.
   * @return The stored time spent value, to which the tracked time
   * would be added.
   */
  protected abstract Integer getStoredValue(TimeTrackingCustomizer customizer, LoadedItem item);

  private void reportTotalTime(TrackerSimplifier ts, Procedure<String> proc) {
    final List<TaskTiming> timings = ts.getTimings();

    int spent = mySpent;
    if(timings != null && !timings.isEmpty()) {
      for(final TaskTiming timing : timings) {
        spent += timing.getLength();
      }
    }

    final String text = DateUtil.getFriendlyDuration(spent, true, true);
    proc.invoke(text);
  }

  @Override
  public void attach(@Nullable final ChangeListener client) {
    myCurrTask = null;
    Database.require().addListener(myLifecycle.lifespan(), new DBListener() {
      public void onDatabaseChanged(DBEvent event, DBReader reader) {
        if(myCurrTask != null) {
          long key = myCurrTask.getKey();
          if(TimeTrackingUtil.eventAffects(event, key)) {
            myCurrTask = null;
            if(client != null) {
              client.onChange();
            }
          }
        }
      }
    });
  }

  @Override
  public void detach() {
    myCurrTask = null;
    myLifecycle.cycle();
  }
}
