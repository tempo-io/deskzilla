package com.almworks.items.sync.edit;

import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.sync.*;
import com.almworks.items.sync.impl.*;
import com.almworks.items.sync.util.ItemDiffImpl;
import com.almworks.items.sync.util.SyncUtils;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.*;

class AutoMerge {
  private final DBWriter myWriter;
  private final long myItem;
  @Nullable
  private final ItemVersion myDoneUpload;
  @Nullable
  private final ItemVersion myDownload;
  @Nullable
  private final ItemVersion myConflict;
  @Nullable
  private final ItemVersion myBase;
  private final ItemAutoMerge myOperations;
  private final SyncManagerImpl myManager;
  private final EditCounterpart myJustEdited;

  private AutoMerge(DBWriter writer, long item, ItemVersion doneUpload, ItemAutoMerge operations,
    SyncManagerImpl manager, ItemVersion download, ItemVersion conflict, ItemVersion base, EditCounterpart justEdited) {
    myWriter = writer;
    myItem = item;
    myDoneUpload = doneUpload;
    myOperations = operations;
    myManager = manager;
    myDownload = download;
    myConflict = conflict;
    myBase = base;
    myJustEdited = justEdited;
  }
  
  public static boolean autoMerge(DBWriter writer, long item, ItemAutoMerge operations, SyncManagerImpl manager,
    EditCounterpart justEdited) {
    AutoMerge autoMerge = new AutoMerge(writer, item, doneUpload(writer, item), operations, manager,
      download(writer, item), SyncUtils.readConflictIfExists(writer, item), SyncUtils.readBaseIfExists(writer, item),
      justEdited);
    return autoMerge.perform();
  }

  private static ItemVersion download(DBReader reader, long item) {
    return BranchUtil.readServerShadow(reader, item, SyncSchema.DOWNLOAD, false);
  }

  private static ItemVersion doneUpload(DBReader reader, long item) {
    return BranchUtil.readServerShadow(reader, item, SyncSchema.DONE_UPLOAD, false);
  }

  public boolean perform() {
    ItemDiffImpl local = ItemDiffImpl.createToTrunk(myWriter, myItem, getTrunkBase());
    if (local != null && local.hasChanges()) myOperations.preProcess(local);
    if (local == null || !local.hasChanges()) return discardLocal(); // No local changes, local edit discarded, last server copied
    ItemVersion newServer = getLastNewServer();
    if (myDoneUpload != null) { // Just uploaded
      return finishUpload(newServer);
    }
    // Has not empty local changes
    List<byte[]> newHistory = local.getUpdatedHistory();
    if (newHistory != null) SyncSchema.writeHistory(myWriter, myItem, newHistory);
    if (newServer == null) return true; // No newer server version, history updated, nothing to do
    if (myBase == null) return discardLocal();
    ItemDiff server = ItemDiffImpl.create(myBase, newServer);
    MergeDataImpl data = MergeDataImpl.create(local, server);
    myOperations.resolve(data);
    if (!data.isConflictResolved()) { // Unresolvable conflict detected
      setNewConflict(newServer);
      return true; // Unresolvable conflict, last DOWNLOAD copied to CONFLICT
    }
    if (data.isResolvedDelete()) { // Resolved to delete
      return physicalDeleteSubtree();  // Deleted or deferred
    }
    if (data.isDiscardEdit()) return discardLocal();
    setNewBase(newServer);
    if (server.hasChanges()) {
      Map<DBAttribute<?>, Object> resolution = data.getResolution();
      for (Map.Entry<DBAttribute<?>, Object> entry : resolution.entrySet()) // Write resolution to trunk
        myWriter.setValue(myItem, (DBAttribute<Object>)entry.getKey(), entry.getValue());
      for (DBAttribute<?> a : server.getChanged()) { // Write server changes to trunk (since there is no conflict with local edit)
        if (resolution.containsKey(a)) continue;
        DBAttribute<Object> attr = (DBAttribute<Object>) a;
        myWriter.setValue(myItem, attr, newServer.getValue(attr));
      }
    }
    if (newServer == myBase && myDownload == null && myConflict == null) return true;
    return new AutoMerge(myWriter, myItem, null, myOperations, myManager, null, null, myBase, myJustEdited).perform();
  }

  private boolean finishUpload(ItemVersion newServer) {
    assert myDoneUpload != null;
    if (myDownload == null || myBase == null) {
      Log.error("Missing upload reason " + myItem + " " + myDownload + " " + myBase);
      forgetUpload(myItem);
      return new AutoMerge(myWriter, myItem, null, myOperations, myManager, null, myConflict, myBase, myJustEdited).perform();
    }
    if (myConflict != null) {
      Log.error("Upload finished when item has conflict " + myItem);
      forgetUpload(myItem);
      return new AutoMerge(myWriter, myItem, null, myOperations, myManager, null, myConflict, myBase, myJustEdited).perform();
    }
    ItemDiffImpl local = ItemDiffImpl.createToTrunk(myWriter, myItem, myDoneUpload);
    if (local != null && local.hasChanges()) myOperations.preProcess(local);
    if (local == null || !local.hasChanges()) return discardLocal(); // No local changes, local edit discarded, last server copied
    ItemDiffImpl server = ItemDiffImpl.create(myBase, myDownload);
    for (DBAttribute<?> a : server.getChanged()) {
      DBAttribute<Object> attribute = (DBAttribute<Object>) a;
      if (!local.isChanged(attribute)) myWriter.setValue(myItem, attribute, server.getNewerValue(attribute));
    }
    setNewBase(newServer);
    return new AutoMerge(myWriter, myItem, null, myOperations, myManager, null, null, SyncUtils.readBaseIfExists(myWriter, myItem), myJustEdited).perform();
  }

  @Nullable
  private ItemVersion getTrunkBase() {
    return myDoneUpload != null ? myDoneUpload : myBase;
  }

  @Nullable
  private ItemVersion getLastNewServer() {
    if (myDownload != null) return myDownload;
    else if (myConflict != null) return myConflict;
    return myBase;
  }

  private static boolean intersects(Collection<?> collection1, Collection<?> collection2) {
    if (collection1 == null || collection2 == null || collection1.isEmpty() || collection2.isEmpty()) return false;
    for (Object o : collection1) {
      if (collection2.contains(o)) return true;
    }
    return false;
  }

  private boolean discardLocal() {
    ItemVersion server = getLastServer();
    if (server == null) return true;
    if (server.getNNValue(SyncSchema.INVISIBLE, false)) return physicalDeleteSubtree();
    SyncSchema.discardSingle(myWriter, myItem);
    markSync(myItem);
    return true;
  }

  @Nullable
  private ItemVersion getLastServer() {
    if (myDownload != null) return myDownload;
    return myConflict != null ? myConflict : myBase;
  }

  private boolean physicalDeleteSubtree() {
    LongList slaves = SyncUtils.getSlavesSubtree(myWriter, myItem);
    if (!myManager.lockOrMergeLater(myWriter, slaves, myJustEdited, LongArray.create(myItem))) return false;
    for (int i = 0; i < slaves.size(); i++) physicalDeleteSingle(slaves.get(i));
    return true;
  }

  private void physicalDeleteSingle(long item) {
    myWriter.clearItem(item);
    markSync(item);
  }

  private void markSync(long item) {
    mergeDone(item, null, null);
  }

  private void setNewBase(ItemVersion base) {
    mergeDone(myItem, base, null);
  }

  private void setNewConflict(ItemVersion conflict) {
    mergeDone(myItem, myBase, conflict);
  }

  private void mergeDone(long item, @Nullable ItemVersion base, @Nullable ItemVersion conflict) {
    HolderCache holders = HolderCache.instance(myWriter);
    holders.setBase(item, base != null ? base.getAllShadowableMap() : null);
    holders.setConflict(item, conflict != null ? conflict.getAllShadowableMap() : null);
    forgetUpload(item);
  }

  private void forgetUpload(long item) {
    HolderCache holders = HolderCache.instance(myWriter);
    holders.setDownload(item, null);
    holders.setDoneUpload(item, null);
    holders.setUploadTask(item, null);
  }

  public static boolean needsMerge(DBReader reader, long item) {
    return HolderCache.instance(reader).hasConflict(item);
  }
}
