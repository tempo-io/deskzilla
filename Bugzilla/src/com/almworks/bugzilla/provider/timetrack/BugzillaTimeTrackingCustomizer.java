package com.almworks.bugzilla.provider.timetrack;

import com.almworks.api.actions.UploadOnSuccess;
import com.almworks.api.application.*;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.comments.LoadedCommentKey;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.items.util.SyncAttributes;
import com.almworks.recentitems.RecentItemUtil;
import com.almworks.timetrack.api.TimeTrackingCustomizer;
import com.almworks.timetrack.gui.ArtifactBoxViewer;
import com.almworks.timetrack.gui.timesheet.GroupingFunction;
import com.almworks.timetrack.impl.*;
import com.almworks.util.Pair;
import com.almworks.util.collections.*;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * The implementation of the {@link TimeTrackingCustomizer} interface
 * using Bugzilla as the back-end.
 */
public class BugzillaTimeTrackingCustomizer implements TimeTrackingCustomizer {
  public static final String DUMMY_COMMENT = "*** Time tracked with Deskzilla ***";
  
  private static final BigDecimal HOUR = new BigDecimal("3600");
  private static final BigDecimal MIN_TIME_HRS = new BigDecimal("0.1");

  private static final Comparator<ItemWrapper> BUG_BY_NUMBER_COMPARATOR =
    new Comparator<ItemWrapper>() {
      @Override
      public int compare(ItemWrapper o1, ItemWrapper o2) {
        int key1 = Util.NN(BugzillaKeys.id.getValue(o1.getLastDBValues()), 0);
        int key2 = Util.NN(BugzillaKeys.id.getValue(o2.getLastDBValues()), 0);
        return Containers.compareInts(key1, key2);
      }
    };

  private static final Convertor<Integer, String> INT_TO_STR = new Convertor<Integer, String>() {
    @Override
    public String convert(Integer value) {
      return "#" + (value == null ? "???" : value.toString());
    }
  };

  private static final Convertor<String, Integer> STR_TO_NULL = Convertors.toNull();

  private static String staticGetItemKey(ItemWrapper a) {
    return INT_TO_STR.convert(BugzillaKeys.id.getValue(a.getLastDBValues()));
  }

  private static Integer toSeconds(BigDecimal hours) {
    if(hours == null) {
      return null;
    }
    return hours.multiply(HOUR).intValue();
  }

  private static BigDecimal toHours(Integer seconds) {
    if(seconds == null) {
      return null;
    }
    return new BigDecimal(seconds).divide(HOUR, 1, RoundingMode.HALF_EVEN);
  }

  @Override
  @NotNull
  public String getItemKey(@NotNull ItemWrapper a) {
    return staticGetItemKey(a);
  }

  @Override
  @NotNull
  public String getItemSummary(@NotNull ItemWrapper a) {
    return BugzillaKeys.summary.getValue(a.getLastDBValues());
  }

  @Override
  @NotNull
  public Pair<String, String> getItemKeyAndSummary(@NotNull ItemWrapper a) {
    final PropertyMap ldv = a.getLastDBValues();
    return Pair.create(
      INT_TO_STR.convert(BugzillaKeys.id.getValue(ldv)),
      BugzillaKeys.summary.getValue(ldv));
  }

  @Override
  public Integer getRemainingTime(@NotNull ItemWrapper a) {
    return toSeconds(BugzillaKeys.remainingTime.getValue(a.getLastDBValues()));
  }

  @Override
  public Integer getTimeSpent(@NotNull ItemWrapper a) {
    return toSeconds(BugzillaKeys.totalWorkTime.getValue(a.getLastDBValues()));
  }

  @Override
  public Integer getTimeSpentByMe(ItemWrapper a) {
    final BugzillaContext context = BugzillaConnection.getContext(a);
    if(context == null) {
      return null;
    }

    final OurConfiguration config = context.getConfiguration().getValue();
    if(config.isAnonymousAccess()) {
      return null;
    }

    final String username1 = config.getUsername();
    final String username2 =
      (username1.indexOf('@') < 0 && config.isUsingEmailSuffix())
        ? (username1 + config.getEmailSuffix())
        : username1;

    int seconds = 0;
    final Collection<LoadedCommentKey> comments = BugzillaKeys.comments.getValue(a.getLastDBValues());
    for(final LoadedCommentKey lck : comments) {
      final String who = lck.getWhoText();
      final BigDecimal hours = lck.getWorkTime();
      if((username1.equalsIgnoreCase(who) || username2.equalsIgnoreCase(who) || !lck.isFinal()) && hours != null) {
        seconds += toSeconds(hours);
      }
    }

    return seconds;
  }

  @Override
  @NotNull
  public List<GroupingFunction> getGroupingFunctions() {
    final List<GroupingFunction> groupings = Collections15.arrayList();
    groupings.add(TimeTrackingUtil.getConnectionGrouping());
    groupings.add(new GroupingFunction() {
      @Override
      @NotNull
      public ItemKey getGroupValue(LoadedItem a) {
        return Util.NN(BugzillaKeys.product.getValue(a.getLastDBValues()), UNKNOWN);
      }
    });
    return groupings;
  }

  @Override
  @NotNull
  public Comparator<ItemWrapper> getArtifactByKeyComparator() {
    return BUG_BY_NUMBER_COMPARATOR;
  }

  @Override
  public boolean isTimeTrackingPermissionGranted(@NotNull ItemWrapper a) {
    final BugzillaContext context = BugzillaConnection.getContext(a);
    if(context != null) {
      final PermissionTracker pt = context.getPermissionTracker();
      if(pt != null) {
        final Boolean ttAllowed = pt.isTimeTrackingAllowed();
        if(ttAllowed != null) {
          return ttAllowed;
        }
      }
    }
    return false;
  }

  @Override
  public EditCommit createPublishingCommit(
    Map<LoadedItem, List<TaskTiming>> timeMap, Map<LoadedItem, TaskRemainingTime> remMap,
    Map<LoadedItem, Integer> deltas, boolean upload)
  {
    final Set<LoadedItem> items = CollectionUtil.setUnion(timeMap.keySet(), remMap.keySet(), deltas.keySet());
    final List<CommitRecord> records = Collections15.arrayList();

    for(final LoadedItem bug : items) {
      final List<String> texts = Collections15.arrayList();
      final List<Integer> times = Collections15.arrayList();

      final List<TaskTiming> timings = timeMap.get(bug);
      prepareComments(timings, texts, times);
      assert texts.size() == times.size();

      handleDelta(deltas.get(bug), texts, times);
      assert texts.size() == times.size();

      removeTooShortIntervals(texts, times);
      assert texts.size() == times.size();

      final List<BigDecimal> hours = convertToHours(times);
      assert hours.size() == times.size();

      final Integer newRemaining = calculateRemaining(remMap.get(bug), bug, timings);
      records.add(new CommitRecord(bug.getItem(), texts, hours, newRemaining));
    }

    final AggregatingEditCommit commit = new AggregatingEditCommit();
    commit.addProcedure(null, new PublishingCommit(records));
    if(upload) {
      commit.addProcedure(null, UploadOnSuccess.create(items));
    }
    return commit;
  }

  private static class CommitRecord {
    final long bug;
    final List<String> texts;
    final List<BigDecimal> hours;
    final Integer remaining;

    public CommitRecord(long bug, List<String> texts, List<BigDecimal> hours, Integer remaining) {
      assert texts.size() == hours.size();
      this.bug = bug;
      this.texts = texts;
      this.hours = hours;
      this.remaining = remaining;
    }
  }

  private static class PublishingCommit extends EditCommit.Adapter {
    private final Collection<CommitRecord> myRecords;

    public PublishingCommit(Collection<CommitRecord> records) {
      myRecords = records;
    }

    @Override
    public void performCommit(EditDrain drain) throws DBOperationCancelledException {
      for(final CommitRecord r : myRecords) {
        if(RecentItemUtil.checkItem(r.bug, drain.getReader())) {
          addComments(r, drain);
          changeRemaining(r, drain);
        }
      }
    }

    private void addComments(CommitRecord r, EditDrain drain) {
      final int n = Math.min(r.texts.size(), r.hours.size());
      for(int i = 0; i < n; i++) {
        final ItemVersionCreator comment = drain.createItem();
        comment.setValue(DBAttribute.TYPE, CommentsLink.typeComment);
        comment.setValue(SyncAttributes.CONNECTION, drain.forItem(r.bug).getValue(SyncAttributes.CONNECTION));
        comment.setValue(CommentsLink.attrMaster, r.bug);
        comment.setValue(CommentsLink.attrText, r.texts.get(i));
        comment.setValue(CommentsLink.attrWorkTime, r.hours.get(i));
      }
    }

    private void changeRemaining(CommitRecord r, EditDrain drain) {
      if(r.remaining != null) {
        drain.changeItem(r.bug).setValue(Bug.attrRemainingTime, toHours(r.remaining));
      }
    }
  }

  private void prepareComments(List<TaskTiming> timings, List<String> texts, List<Integer> times) {
    int last = -1;
    if(timings != null) {
      Collections.sort(timings);
      for(final TaskTiming timing : timings) {
        String text = timing.getComments();
        if(text == null || text.trim().isEmpty()) {
          text = DUMMY_COMMENT;
        }
        if(last < 0 || !text.equals(texts.get(last))) {
          texts.add(text);
          times.add(timing.getLength());
          last++;
        } else {
          times.set(last, times.get(last) + timing.getLength());
        }
      }
    }
  }

  private void handleDelta(Integer delta, List<String> texts, List<Integer> times) {
    if(delta != null && delta != 0) {
      if(texts.isEmpty()) {
        texts.add(DUMMY_COMMENT);
        times.add(delta);
      } else {
        // Proportional distribution.
        TimeTrackingUtil.distributeDelta(times, delta);
      }
    }
  }

  private void removeTooShortIntervals(List<String> texts, List<Integer> times) {
    for(int i = times.size() - 1; i >= 0; i--) {
      if(times.get(i) < TimeTrackingUtil.MINIMAL_INTERVAL_SEC) {
        texts.remove(i);
        times.remove(i);
      }
    }
  }

  private List<BigDecimal> convertToHours(List<Integer> times) {
    // If duration in hours is less than minimal (0.1h for Bugzilla),
    // we add the minimal time instead.
    final List<BigDecimal> hours = Collections15.arrayList();
    for(final int seconds : times) {
      final BigDecimal h = toHours(seconds);
      if(h.abs().compareTo(MIN_TIME_HRS) >= 0) {
        hours.add(h);
      } else {
        hours.add(h.signum() < 0 ? MIN_TIME_HRS.negate() : MIN_TIME_HRS);
      }
    }
    return hours;
  }

  private Integer calculateRemaining(TaskRemainingTime remaining, LoadedItem a, List<TaskTiming> timings) {
    if(remaining == null) {
      final Integer oldRemaining = getRemainingTime(a);
      if(oldRemaining != null) {
        remaining = TaskRemainingTime.old(oldRemaining);
      }
    }
    return TimeTrackingUtil.getRemainingTimeForTimings(timings, remaining, false);
  }

  @Override
  public JComponent createBoxViewer(Lifespan life, @NotNull ItemWrapper a) {
    return new ArtifactBoxViewer(life, BugzillaKeys.id, INT_TO_STR, STR_TO_NULL, BugzillaKeys.summary).getComponent();
  }
}
