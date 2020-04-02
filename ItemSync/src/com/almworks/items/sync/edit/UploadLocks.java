package com.almworks.items.sync.edit;

import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBWriter;
import com.almworks.items.sync.impl.HolderCache;
import com.almworks.items.sync.impl.SyncSchema;
import com.almworks.items.util.DBNamespace;
import com.almworks.util.collections.*;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.Map;

class UploadLocks {
  private static final DBNamespace NS = SyncSchema.NS.subNs("upload");
  private static final DBAttribute<byte[]> AT_MERGE_WHEN_UPLOADED = DBAttribute.Scalar(NS.attr("mergeWhenUploaded"), "Merge Items When Uploaded", byte[].class);

  // Guarded by myCurrentTasks
  private final Map<UploadTask, LongSet> myCurrentTasks = Collections15.hashMap();
  private final SimpleModifiable myModifiable;

  UploadLocks(SimpleModifiable modifiable) {
    myModifiable = modifiable;
  }

  public boolean registerTask(UploadTask task, LongList items) {
    synchronized (myCurrentTasks) {
      if (priIsAnyLocked(task, items)) return false;
      LongSet set = myCurrentTasks.get(task);
      if (set == null) {
        set = new LongSet();
        myCurrentTasks.put(task, set);
      }
      set.addAll(items);
    }
    myModifiable.fireChanged();
    return true;
  }

  private boolean priIsAnyLocked(@Nullable UploadTask task, LongList items) {
    assert Thread.holdsLock(myCurrentTasks);
    for (Map.Entry<UploadTask, LongSet> entry : myCurrentTasks.entrySet()) {
      if (entry.getKey() == task) continue;
      for (int i = 0; i < items.size(); i++) {
        long item = items.get(i);
        if (entry.getValue().contains(item))
          return true;
      }
    }
    return false;
  }

  public void unregisterTask(UploadTask task) {
    boolean changed;
    synchronized (myCurrentTasks) {
      changed = myCurrentTasks.remove(task) != null;
    }
    if (changed) myModifiable.fireChanged();
  }

  /**
   * Checks if item can be merged during this write transaction (not during upload). If any of the items is locked then
   * enquires itemsToMerge to be merged when the locks released (upload finished)
   * @param writer the current transaction
   * @param items required for merge
   * @param itemsToEnqueue an item that should be merged later if the item is locked for upload
   * @return true iff the item merge is allowed during this transaction. false means that itemToMerge is going to be merged later
   */
  public static boolean allowsMerge(DBWriter writer, LongList items, LongList itemsToEnqueue) {
    LongList uploading = selectUploading(writer, items);
    if (uploading.isEmpty()) return true;
    for (int i = 0; i < uploading.size(); i++) {
      addMergeWhenUploaded(writer, uploading.get(i), itemsToEnqueue);
    }
    return false;
  }

  private static void addMergeWhenUploaded(DBWriter writer, long item, LongList itemsToEnqueue) {
    byte[] serialized = AT_MERGE_WHEN_UPLOADED.getValue(item, writer);
    ByteArray bytes = serialized != null ? ByteArray.wrap(serialized) : new ByteArray(8);
    if (addAll(bytes, itemsToEnqueue)) AT_MERGE_WHEN_UPLOADED.setValue(writer, item, bytes.toNativeArray());
  }

  private static LongList selectUploading(DBWriter writer, LongList items) {
    LongArray uploading = new LongArray();
    for (int i = 0; i < items.size(); i++) {
      long item = items.get(i);
      if (HolderCache.instance(writer).hasUploadTask(item)) uploading.add(item);
    }
    return uploading;
  }

  public static LongList selectNotUploadingDeferOther(DBWriter writer, LongList items) {
    LongList uploading = selectUploading(writer, items);
    if (uploading.isEmpty()) return items;
    LongSet result = new LongSet();
    result.addAll(items);
    result.removeAll(uploading);
    for (int i = 0; i < uploading.size(); i++) {
      long item = uploading.get(i);
      addMergeWhenUploaded(writer, item, LongArray.singleton(item));
    }
    return result;
  }

  private static boolean addAll(ByteArray bytes, LongList items) {
    if (bytes.size() % 8  != 0) {
      Log.error("Wrong size " + bytes);
      bytes.setSize((bytes.size() / 8) * 8);
    }
    boolean changed = false;
    for (int i = 0; i < items.size(); i++) {
      long item = items.get(i);
      boolean found = false;
      for (int j = 0; j < bytes.size() / 8; j++) {
        if (item == bytes.getLong(j * 8)) {
          found = true;
          break;
        }
      }
      if (!found) {
        bytes.addLong(item);
        changed = true;
      }
    }
    return changed;
  }

  public boolean isRegistered(UploadTask task) {
    synchronized (myCurrentTasks) {
      return myCurrentTasks.containsKey(task);
    }
  }

  @NotNull
  public long[] getLocked(UploadTask task) {
    synchronized (myCurrentTasks) {
      LongSet set = myCurrentTasks.get(task);
      return set != null ? set.toNativeArray() : Const.EMPTY_LONGS;
    }
  }

  public boolean isLocked(UploadTask task, long item) {
    synchronized (myCurrentTasks) {
      LongSet locked = myCurrentTasks.get(task);
      return locked != null && locked.contains(item);
    }
  }

  public void unregister(UploadTask task, LongList items) {
    boolean changed;
    synchronized (myCurrentTasks) {
      LongSet set = myCurrentTasks.get(task);
      if (set == null) return;
      changed = set.removeAll(items);
      if (set.isEmpty()) myCurrentTasks.remove(task);
    }
    if (changed) myModifiable.fireChanged();
  }

  public boolean isAnyLocked(LongList items) {
    if (items == null || items.isEmpty()) return false;
    synchronized (myCurrentTasks) {
      return priIsAnyLocked(null, items);
    }
  }
}
