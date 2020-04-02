package com.almworks.items.sync.edit;

import com.almworks.integers.LongList;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.AutoMergeData;
import com.almworks.items.sync.ItemDiff;
import com.almworks.items.sync.impl.SyncSchema;
import com.almworks.items.sync.util.ItemDiffImpl;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.*;

class MergeDataImpl implements AutoMergeData {
  private final Set<DBAttribute<?>> myConflicts = Collections15.hashSet();
  private final Map<DBAttribute<?>, Object> myResolution = Collections15.hashMap();
  private final Set<DBAttribute<?>> myDiscarded = Collections15.hashSet();
  private final ItemDiff myLocal;
  private final ItemDiff myServer;

  public MergeDataImpl(ItemDiff local, ItemDiff server) {
    myLocal = local;
    myServer = server;
    assert local.getItem() == server.getItem();
  }

  public static MergeDataImpl create(@NotNull ItemDiff local, @NotNull ItemDiff server) {
    long item = local.getItem();
    if (item != server.getItem()) Log.error("Different items merged " + local + " " + server);
    MergeDataImpl data = new MergeDataImpl(local, server);
    Collection<? extends DBAttribute<?>> localChanges = local.getChanged();
    Collection<? extends DBAttribute<?>> serverChanges = server.getChanged();
    data.myConflicts.addAll(localChanges);
    data.myConflicts.retainAll(serverChanges);
    if (!data.myConflicts.contains(SyncSchema.INVISIBLE)
      && local.getNewerVersion().isInvisible() != server.getNewerVersion().isInvisible()) {
      if ((localChanges.contains(SyncSchema.INVISIBLE) && local.getNewerVersion().isInvisible() && !serverChanges.isEmpty())
        || serverChanges.contains(SyncSchema.INVISIBLE) && server.getNewerVersion().isInvisible() && !localChanges.isEmpty())
        data.myConflicts.add(SyncSchema.INVISIBLE);
    }
    for (Iterator<DBAttribute<?>> iterator = data.myConflicts.iterator(); iterator.hasNext();) {
      DBAttribute<?> attribute = iterator.next();
      if (ItemDiffImpl.isEqualNewer(local, server, attribute)) iterator.remove();
    }
    return data;
  }

  public Map<DBAttribute<?>, Object> getResolution() {
    return Collections.unmodifiableMap(myResolution);
  }

  public boolean isConflictResolved() {
    return isResolvedDelete() || myConflicts.isEmpty();
  }

  @Override
  public long getItem() {
    return myLocal.getItem();
  }

  @Override
  public Collection<DBAttribute<?>> getUnresolved() {
    return Collections15.arrayList(myConflicts);
  }

  @Override
  public <T> void discardEdit(DBAttribute<T> attribute) {
    if (!myLocal.isChanged(attribute)) return;
    T serverValue = myServer.getNewerValue(attribute);
    setResolution(attribute, serverValue);
  }

  @Override
  public void setCompositeResolution(DBAttribute<? extends Collection<? extends Long>> attribute, LongList resolution) {
    if (attribute == null) return;
    if (resolution == null) resolution = LongList.EMPTY;
    DBAttribute.ScalarComposition composition = attribute.getComposition();
    Collection<Long> objResolution;
    switch (composition) {
    case SCALAR: Log.error("Expected composite " + attribute); return;
    case LIST: objResolution = resolution.toList(); break;
    case SET: objResolution = Collections15.hashSet(resolution.toList()); break;
    default: Log.error("Unknown composition " + attribute); return;
    }
    //noinspection unchecked
    setResolution((DBAttribute<Collection<? extends Long>>) (DBAttribute)attribute, objResolution);
  }

  @Override
  public void setCompositeResolution(DBAttribute<? extends Collection<? extends String>> attribute,
    Collection<String> resolution)
  {
    if (attribute == null) return;
    if (resolution == null) resolution = Collections.emptySet();
    DBAttribute.ScalarComposition composition = attribute.getComposition();
    Collection<String> fixedResolution;
    switch (composition) {
    case SCALAR: Log.error("Expected composite " + attribute); return;
    case SET: fixedResolution = resolution instanceof Set<?> ? resolution : Collections15.hashSet(resolution); break;
    case LIST: fixedResolution = resolution instanceof List<?> ? resolution : Collections15.arrayList(resolution); break;
    default: Log.error("Unknown composition " + attribute); return;
    }
    //noinspection unchecked
    setResolution((DBAttribute<Collection<? extends String>>) (DBAttribute)attribute, fixedResolution);
  }

  @Override
  public <T> void setResolution(DBAttribute<T> attribute, T value) {
    T serverNew = myServer.getNewerValue(attribute);
    if (ItemDiffImpl.isEqualValue(getReader(), attribute, serverNew, value)) myDiscarded.add(attribute);
    myResolution.put(attribute, value);
    myConflicts.remove(attribute);
  }

  @Override
  public ItemDiff getLocal() {
    return myLocal;
  }

  @Override
  public ItemDiff getServer() {
    return myServer;
  }

  public boolean isResolvedDelete() {
    if (!Boolean.TRUE.equals(myServer.getNewerValue(SyncSchema.INVISIBLE))) return false;
    if (myLocal.isChanged(SyncSchema.INVISIBLE)) {
      return Boolean.TRUE.equals(myResolution.get(SyncSchema.INVISIBLE));
    } else return Boolean.TRUE.equals(myLocal.getNewerValue(SyncSchema.INVISIBLE));
  }

  public boolean isDiscardEdit() {
    if (myLocal.hasHistory()) return false;
    return myDiscarded.containsAll(myLocal.getChanged());
  }

  @Override
  public DBReader getReader() {
    return getLocal().getReader();
  }
}
