package com.almworks.api.engine.util;

import com.almworks.api.engine.PrimaryItemStructure;
import com.almworks.engine.items.ItemStorageAdaptor;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.LongSet;
import org.almworks.util.ArrayUtil;
import org.jetbrains.annotations.*;

public class FixedPrimaryItemStructure implements PrimaryItemStructure {
  private final BoolExpr<DP> myPrimary;
  private final BoolExpr<DP> myModified;
  private final BoolExpr<DP> myConflict;
  private final BoolExpr<DP> myUploadable;
  private final DBAttribute<Long>[] myMasterAttributes;

  public FixedPrimaryItemStructure(DBItemType primaryItemType, DBAttribute<Long> ... masterAttributes) {
    myMasterAttributes = ArrayUtil.arrayCopy(masterAttributes);
    myPrimary = DPEqualsIdentified.create(DBAttribute.TYPE, primaryItemType);
    myModified = BoolExpr.and(ItemStorageAdaptor.modified(masterAttributes), myPrimary);
    myConflict = BoolExpr.and(ItemStorageAdaptor.inConflict(masterAttributes), myPrimary);
    myUploadable = myModified.and(myConflict.negate());
  }

  @NotNull
  @Override
  public BoolExpr<DP> getPrimaryItemsFilter() {
    return myPrimary;
  }

  @NotNull
  @Override
  public BoolExpr<DP> getLocallyChangedFilter() {
    return myModified;
  }

  @NotNull
  @Override
  public BoolExpr<DP> getConflictingItemsFilter() {
    return myConflict;
  }

  @NotNull
  @Override
  public BoolExpr<DP> getUploadableItemsFilter() {
    return myUploadable;
  }

  @NotNull
  @Override
  public LongList loadEditableSlaves(ItemVersion primary) {
    if (myMasterAttributes.length == 0) return LongList.EMPTY;
    LongSet slaves = new LongSet();
    for (DBAttribute<Long> master : myMasterAttributes) {
      slaves.addAll(primary.getSlaves(master));
    }
    return slaves.isEmpty() ? LongList.EMPTY : slaves;
  }
}
