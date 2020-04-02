package com.almworks.items.sync.impl;

import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.SyncState;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.AttributeMap;
import com.almworks.util.collections.LongSet;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.Map;

abstract class VersionReader extends BaseVersionReader {
  public abstract VersionHolder getHolder();

  public DBAttribute<AttributeMap> getShadow() {
    return getHolder().getShadow();
  }

  @Override
  @NotNull
  public DBReader getReader() {
    return getHolder().getReader();
  }

  @Override
  public <T> T getValue(DBAttribute<T> attribute) {
    if (attribute == null) return null;
    return getHolder().getValue(attribute);
  }

  @Override
  public AttributeMap getAllShadowableMap() {
    return getHolder().getAllShadowableMap();
  }

  @Override
  public AttributeMap getAllValues() {
    return getHolder().getAllValues();
  }

  @Override
  public long getIcn() {
    return getReader().getItemIcn(getItem());
  }

  protected BranchUtil getUtil() {
    return BranchUtil.instance(getReader());
  }

  @Override
  public LongList getSlavesRecursive() {
    return SyncUtils.getSlavesSubtree(getReader(), getItem());
  }

  @NotNull
  @Override
  public LongList getLongSet(DBAttribute<? extends Collection<? extends Long>> attribute) {
    Collection<? extends Long> list = getValue(attribute);
    if (list == null || list.isEmpty()) return LongList.EMPTY;
    LongSet result = new LongSet(list.size());
    for (Long aLong : list) if (aLong != null) result.add(aLong);
    return result;
  }

  @NotNull
  @Override
  public LongArray getSlaves(DBAttribute<Long> masterReference) {
    return getReader().query(DPEquals.create(masterReference, getItem())).copyItemsSorted();
  }

  @Override
  public SyncState getSyncState() {
    HolderCache holders = HolderCache.instance(getReader());
    AttributeMap base = holders.getBase(getItem());
    if (base == null) return SyncState.SYNC;
    if (SyncSchema.isInvisible(base)) return SyncState.NEW;
    boolean deleted = SyncUtils.isTrunkInvisible(getReader(), getItem());
    AttributeMap conflict = holders.getConflict(getItem());
    if (conflict == null) return deleted ? SyncState.LOCAL_DELETE : SyncState.EDITED;
    boolean remoteDelete = SyncSchema.isInvisible(conflict);
    if (remoteDelete) return SyncState.MODIFIED_CORPSE;
    return deleted ? SyncState.DELETE_MODIFIED : SyncState.CONFLICT;
  }

  @Override
  public boolean isInvisible() {
    Boolean value = getValue(SyncSchema.INVISIBLE);
    return value != null && value;
  }

  @NotNull
  @Override
  public ItemVersion readTrunk(long item) {
    return getUtil().readItem(item, Branch.TRUNK);
  }

  @Override
  public <T> T mapValue(DBAttribute<Long> attribute, Map<? extends DBIdentifiedObject, T> map) {
    for (Map.Entry<? extends DBIdentifiedObject, T> entry : map.entrySet()) {
      if (equalValue(attribute, entry.getKey())) return entry.getValue();
    }
    return null;
  }

  @Override
  public ItemVersion readValue(DBAttribute<Long> attribute) {
    Long value = getValue(attribute);
    return value != null ? forItem(value) : null;
  }

  @Override
  public ItemVersion switchToServer() {
    return SyncUtils.readServerIfExists(getReader(), getItem());
  }

  @NotNull
  @Override
  public ItemVersion switchToTrunk() {
    Branch branch = getBranch();
    return branch == Branch.TRUNK ? this : SyncUtils.readTrunk(getReader(), getItem());
  }
}
