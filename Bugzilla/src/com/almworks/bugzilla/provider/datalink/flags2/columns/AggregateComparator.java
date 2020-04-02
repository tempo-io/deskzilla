package com.almworks.bugzilla.provider.datalink.flags2.columns;

import com.almworks.api.application.LoadedItem;
import com.almworks.bugzilla.provider.datalink.flags2.*;
import com.almworks.util.collections.Containers;
import com.almworks.util.commons.Condition;
import org.jetbrains.annotations.*;

import java.util.*;

class AggregateComparator extends FlagsComparator<FlagVersion> {
  @Nullable
  private final Character myStatus;
  private final Comparator<Collection<? extends FlagVersion>> myResolvedFlagComparator = Containers.collectionComparator(false, Flag.ORDER);

  AggregateComparator(Character status) {
    myStatus = status;
  }

  @Override
  protected int countFlags(List<? extends FlagVersion> flags, char status, boolean accountForMe) {
    int count = 0;
    for (FlagVersion flag : flags) {
      if (flag.getStatus().getChar() != status) continue;
      if (!accountForMe || flag.isThisUserRequested()) count++;
    }
    return count;
  }

  @Override
  protected boolean isSingleStatus() {
    return myStatus != null;
  }

  @Override
  protected int compareByStatus(List<? extends FlagVersion> o1, List<? extends FlagVersion> o2) {
    assert myStatus != null;
    int cmp = 0;
    if (myStatus == '?') {
      cmp = compareCount(o1, o2, myStatus, true);
    }
    if (cmp == 0) cmp = compareCount(o1, o2, myStatus, false);
    if (cmp == 0) cmp = compareNames(o1, o2);
    return cmp;
  }

  @Override
  protected int compareNames(List<? extends FlagVersion> o1, List<? extends FlagVersion> o2) {
    return myResolvedFlagComparator.compare(o1, o2);
  }

  public static Comparator<LoadedItem> create(final Character status) {
    final Condition<FlagVersion> statusCondition = status == null
        ? Condition.<FlagVersion>always()
        : new Condition<FlagVersion>() {
          @Override
          public boolean isAccepted(FlagVersion value) {
            return value.getStatusChar() == status;
          }
    };
    final AggregateComparator aggregateComparator = new AggregateComparator(status);
    return new Comparator<LoadedItem>() {
      @Override
      public int compare(LoadedItem o1, LoadedItem o2) {
        return aggregateComparator.compare(selectFlags(o1), selectFlags(o2));
      }

      private List<FlagVersion> selectFlags(LoadedItem loadedItem) {
        List<FlagVersion> flags = FlagsModelKey.getAllFlags(loadedItem.getValues(), false);
        return statusCondition.select(flags);
      }
    };
  }
}
