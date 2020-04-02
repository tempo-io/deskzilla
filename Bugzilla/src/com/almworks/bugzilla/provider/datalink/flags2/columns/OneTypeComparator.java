package com.almworks.bugzilla.provider.datalink.flags2.columns;

import com.almworks.api.application.LoadedItem;
import com.almworks.bugzilla.provider.datalink.flags2.*;
import com.almworks.util.collections.*;
import com.almworks.util.commons.Condition;
import org.jetbrains.annotations.*;

import java.util.Comparator;
import java.util.List;

import static com.almworks.util.collections.Convertor.conv;

class OneTypeComparator extends FlagsComparator<FlagVersion> {
  @NotNull
  private final FlagTypeItem myType;

  OneTypeComparator(FlagTypeItem type) {
    myType = type;
  }

  @Override
  protected int countFlags(List<? extends FlagVersion> flags, char status, boolean accountForMe) {
    int count = 0;
    for (FlagVersion flag : flags) {
      if (flag.getStatusChar() != status) continue;
      if (flag.getTypeItem() != myType.getResolvedItem()) continue;
      if (!accountForMe || flag.isThisUserRequested()) count++;
    }
    return count;
  }

  public static Comparator<LoadedItem> create(final FlagTypeItem type) {
    Condition<FlagVersion> myType = new Condition<FlagVersion>() {
      @Override
      public boolean isAccepted(FlagVersion value) {
        return !value.isDeleted() && value.getTypeItem() == type.getResolvedItem();
      }
    };
    ReadAccessor<LoadedItem, List<FlagVersion>> accessor =
      LoadedItem.GET_VALUES.composition(
      FlagsModelKey.GET_FLAGS.composition(
      conv(Functional.<FlagVersion>filterToList(myType)))).toReadAccessor();
    return Containers.<LoadedItem, List<? extends FlagVersion>>convertingComparator(accessor, new OneTypeComparator(type));
  }
}
