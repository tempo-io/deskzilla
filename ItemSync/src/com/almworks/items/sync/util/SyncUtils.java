package com.almworks.items.sync.util;

import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.dp.DPNotNull;
import com.almworks.items.sync.*;
import com.almworks.items.sync.impl.*;
import com.almworks.items.util.*;
import com.almworks.util.Pair;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.LongSet;
import com.almworks.util.commons.Procedure;
import org.almworks.util.Collections15;
import org.almworks.util.Const;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;
import util.concurrent.Synchronized;

import java.util.*;

public class SyncUtils {
  /**
   * Deletes direct slaves referred via specified masterAttribute
   * @param master master item
   * @param masterAttribute slave to master reference
   */
  public static void deleteAllSlaves(ItemVersionCreator master, DBAttribute<Long> masterAttribute) {
    LongList slaves = master.getSlaves(masterAttribute);
    for (int i = 0; i < slaves.size(); i++) master.changeItem(slaves.get(i)).delete();
  }

  public static ItemVersion readTrunk(DBReader reader, long item) {
    return BranchUtil.instance(reader).readItem(item, Branch.TRUNK);
  }

  public static ItemVersion readTrunk(DBReader reader, DBIdentifiedObject object) {
    long item = reader.findMaterialized(object);
    return readTrunk(reader, item);
  }

  public static <T> long findOrCreate(DBDrain drain, BoolExpr<DP> expr, DBAttribute<T> attribute, T key,
    Procedure<ItemVersionCreator> initializer)
  {
    final long found = drain.getReader().query(expr).getItemByKey(attribute, key);
    if(found > 0) {
      return found;
    }

    final ItemVersionCreator newItem = drain.createItem();
    initializer.invoke(newItem);
    return newItem.getItem();
  }

  public static void deleteAll(DBDrain drain, LongList items) {
    if (items == null || items.isEmpty()) return;
    for (int i = 0; i < items.size(); i++) drain.changeItem(items.get(i)).delete();
  }

  public static DBQuery queryFailedUploads(DBQuery query) {
    return query.query(
      BoolExpr.and(DPEquals.create(SyncSchema.UPLOAD_FAILED, true), DPNotNull.create(SyncSchema.BASE)));
  }

  public static List<ItemVersion> readItems(final DBReader reader, final LongList items) {
    if (items == null || items.isEmpty()) return Collections15.emptyList();
    return new AbstractList<ItemVersion>() {
      @Override
      public ItemVersion get(int index) {
        return readTrunk(reader, items.get(index));
      }

      @Override
      public int size() {
        return items.size();
      }
    };
  }

  public static <T> List<ItemVersion> selectItems(List<ItemVersion> items, DBAttribute<T> attr, T value) {
    HashMap<DBAttribute<?>, Object> sample = Collections15.hashMap();
    sample.put(attr, value);
    return selectItems(items, sample);
  }

  public static List<ItemVersion> selectItems(List<ItemVersion> bugs, Map<DBAttribute<?>, Object> sample) {
    List<ItemVersion> candidate = Collections15.arrayList();
    for (ItemVersion bug : bugs) {
      boolean matches = true;
      for (Map.Entry<DBAttribute<?>, Object> entry : sample.entrySet()) {
        if (!DatabaseUtil.isEqualValue((DBAttribute)entry.getKey(), entry.getValue(), bug.getValue(entry.getKey()))) {
          matches = false;
          break;
        }
      }
      if (matches) candidate.add(bug);
    }
    return candidate;
  }

  /**
   * @see #getSlavesSubtree(com.almworks.items.api.DBReader, long)
   */
  public static LongList getSlavesSubtrees(DBReader reader, LongList masters) {
    if (masters == null || masters.isEmpty()) return LongList.EMPTY;
    List<DBAttribute<Long>> masterAttributes = BadUtil.getMasterAttributes(reader);
    LongSet result = new LongSet();
    for (int i = 0; i < masters.size(); i++) {
      long master = masters.get(i);
      if (result.contains(master)) continue;
      result.add(master);
      collectSlavesRecursive(reader, master, result, masterAttributes);
    }
    return result;
  }

  /**
   * @return all slaves of the given item and the item itself
   */
  @NotNull
  public static LongList getSlavesSubtree(DBReader reader, long item) {
    LongSet result = new LongSet();
    result.add(item);
    collectSlavesRecursive(reader, item, result, BadUtil.getMasterAttributes(reader));
    return result;
  }

  public static void collectSlavesRecursive(DBReader reader, long item, LongSet target,
    List<DBAttribute<Long>> masterAttributes)
  {
    List<BoolExpr<DP>> exprs = Collections15.arrayList();
    for (DBAttribute<Long> attribute : masterAttributes) exprs.add(DPEquals.create(attribute, item));
    LongArray slaves = reader.query(BoolExpr.or(exprs)).copyItemsSorted();
    for (int i = 0; i < slaves.size(); i++) {
      long slave = slaves.get(i);
      if (target.contains(slave)) continue;
      target.add(slave);
      collectSlavesRecursive(reader, slave, target, masterAttributes);
    }
  }

  @Nullable
  public static ItemVersion readBaseIfExists(DBReader reader, long item) {
    return BranchUtil.readServerShadow(reader, item, SyncSchema.BASE, false);
  }

  public static ItemVersion readConflictIfExists(DBReader reader, long item) {
    return BranchUtil.readServerShadow(reader, item, SyncSchema.CONFLICT, false);
  }

  public static ItemVersion readServerIfExists(DBReader reader, long item) {
    return BranchUtil.instance(reader).readServerIfExists(item);
  }

  public static boolean isNew(long item, DBReader reader) {
    if (item <= 0) return false;
    HolderCache cache = HolderCache.instance(reader);
    VersionHolder holder = cache.getHolder(item, SyncSchema.BASE, false);
    if (holder == null || !Boolean.TRUE.equals(holder.getValue(SyncSchema.INVISIBLE))) return false;
    return cache.getHolder(item, SyncSchema.DOWNLOAD, false) == null && cache.getHolder(item, SyncSchema.CONFLICT, false) == null;
  }

  public static boolean isTrunkInvisible(DBReader reader, long item) {
    return isInvisible(item, reader, null);
  }

  private static boolean isInvisible(long item, DBReader reader, @Nullable DBAttribute<AttributeMap> shadow) {
    if (item <= 0) return false;
    VersionHolder holder = HolderCache.instance(reader).getHolder(item, shadow, false);
    return holder != null && Boolean.TRUE.equals(holder.getValue(SyncSchema.INVISIBLE));
  }

  @Nullable
  public static ItemVersion getLatestServer(long item, DBReader reader) {
    return BranchUtil.instance(reader).readServerIfExists(item);
  }

  @SuppressWarnings({"unchecked"})
  public static void copyValues(ItemVersionCreator creator, AttributeMap values) {
    for (DBAttribute attribute : values.keySet()) {
      creator.setValue(attribute, values.get(attribute));
    }
  }

  @SuppressWarnings({"unchecked"})
  public static void copyValues(ItemVersionCreator creator, Map<DBAttribute<?>, Object> values) {
    for (Map.Entry<DBAttribute<?>, Object> entry : values.entrySet()) {
      Object value = entry.getValue();
      DBAttribute<?> attribute = entry.getKey();
      if (value instanceof DBIdentifiedObject) creator.setValue(((DBAttribute<Long>) attribute), (DBIdentifiedObject)value);
      else creator.setValue((DBAttribute<Object>) attribute, value);
    }
  }

  /**
   * Notifies that all items of the given type are uploaded and should be unlocked.<br>
   * Use this method for items which do not require special post-upload check and can detect successful upload via
   * downloaded state inspection.
   * @param drain upload drain
   * @param type items type
   */
  public static void setAllUploaded(UploadDrain drain, DBItemType type) {
    for (ItemVersion item : drain.readItems(drain.getLockedForUpload())) {
      if (item.equalValue(DBAttribute.TYPE, type)) drain.setAllDone(item.getItem());
    }
  }

  public static boolean isRemoved(ItemVersion item) {
    return item == null || item.getValue(SyncAttributes.EXISTING) == null;
  }

  public static ItemUploader.UploadProcess syncUpload(SyncManager manager, LongList items, final ItemUploader itemPrepare) {
    final Synchronized<Pair<Boolean, ItemUploader.UploadProcess>> result = Synchronized.create(null);
    Lifespan life = manager.upload(items, new ItemUploader() {
      @Override
      public void prepare(UploadPrepare prepare, ItemVersion trunk, boolean uploadAllowed) {
        itemPrepare.prepare(prepare, trunk, uploadAllowed);
      }

      @Override
      public void doUpload(UploadProcess process) {
        boolean ok = process != null;
        boolean committed = result.commit(null, Pair.create(ok, process));
        if (!committed && process != null) process.uploadDone();
      }
    });
    if (life == null) {
      setFailure(result);
      return null;
    }
    life.add(new Detach() {
      @Override
      protected void doDetach() throws Exception {
        setFailure(result);
      }
    });
    try {
      result.waitForNotNull(20* Const.SECOND);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      setFailure(result);
      return null;
    }
    Pair<Boolean, ItemUploader.UploadProcess> pair = result.get();
    if (pair == null) {
      setFailure(result);
      return null;
    }
    ItemUploader.UploadProcess process = pair.getSecond();
    assert Boolean.TRUE.equals(pair.getFirst()) == (process != null);
    return pair.getSecond();
  }

  private static void setFailure(Synchronized<Pair<Boolean, ItemUploader.UploadProcess>> result) {
    while (true) {
      Pair<Boolean, ItemUploader.UploadProcess> pair = result.get();
      if (pair != null) return;
      if (result.commit(null, Pair.<Boolean, ItemUploader.UploadProcess>create(false, null))) return;
    }
  }
}
