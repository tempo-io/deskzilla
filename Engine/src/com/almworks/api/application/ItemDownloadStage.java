package com.almworks.api.application;

import com.almworks.items.api.DP;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

public enum ItemDownloadStage {
  NEW(0),
  DUMMY(1),
  STALE(2),
  QUICK(3),
  FULL(4);

  public static final ItemDownloadStage DEFAULT = QUICK;
  public static final BoolExpr<DP> IS_NEW = DPEquals.create(SyncAttributes.ITEM_DOWNLOAD_STAGE, NEW.getDbValue());

  private final Integer myDbValue;

  ItemDownloadStage(Integer dbValue) {
    myDbValue = dbValue;
  }

  public Integer getDbValue() {
    return myDbValue;
  }

  @NotNull
  public static ItemDownloadStage fromDbValue(Integer dbValue) {
    if (dbValue == null) return DEFAULT;
    for (ItemDownloadStage s : values()) {
      if (s.myDbValue == dbValue) return s;
    }
    assert false : dbValue;
    Log.warn("unknown stage id " + dbValue);
    return DEFAULT;
  }

  public boolean wasFull() {
    return this == STALE || this == FULL;
  }

  public void setToCreator(ItemVersionCreator creator) {
    int value = getDbValue();
    // todo this method contradicts {@link wasFull} : if NEW is replaced by QUICK, STALE is set, although the item has never been in FULL state.
    if (this != FULL) {
      Integer current = creator.getValue(SyncAttributes.ITEM_DOWNLOAD_STAGE);
      if (current != null &&
        (current.equals(ItemDownloadStage.FULL.getDbValue()) ||
         current.equals(ItemDownloadStage.STALE.getDbValue()) ||
         current.equals(ItemDownloadStage.NEW.getDbValue())))
        value = ItemDownloadStage.STALE.getDbValue();
      else
        value = ItemDownloadStage.QUICK.getDbValue();
    }
    creator.setValue(SyncAttributes.ITEM_DOWNLOAD_STAGE, value);
  }

  @NotNull
  public static ItemDownloadStage getValue(ItemVersion item) {
    Integer value = item.getValue(SyncAttributes.ITEM_DOWNLOAD_STAGE);
    return fromDbValue(value);
  }
}
