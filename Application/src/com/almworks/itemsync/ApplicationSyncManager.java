package com.almworks.itemsync;

import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.sync.*;
import com.almworks.items.sync.edit.SyncManagerImpl;
import com.almworks.util.collections.Modifiable;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;
import org.picocontainer.Startable;

public class ApplicationSyncManager implements SyncManager, ItemAutoMerge.Selector, Startable {
  private final SyncManagerImpl myManager;
  private final MergeOperationsManagerImpl myOperations;

  public ApplicationSyncManager(Database db, MergeOperationsManagerImpl operations) {
    myOperations = operations;
    myManager = new SyncManagerImpl(db, this);
  }

  @Override
  public void start() {
    myManager.runStartup();
  }

  @Override
  public void stop() {
    myOperations.clear();
  }

  @Override
  public ItemAutoMerge getOperations(DBReader reader, long item) {
    return myOperations.getOperations(reader, item);
  }

  @Override
  public boolean canUpload(long item) {
    return myManager.canUpload(item);
  }

  @Override
  public boolean canUploadAll(LongList items) {
    return myManager.canUploadAll(items);
  }

  @Override
  public boolean isDuringUpload(DBReader reader, long item) {
    return myManager.isDuringUpload(reader, item);
  }

  @Override
  public void commitEdit(EditCommit commit) {
    myManager.commitEdit(commit);
  }

  @Override
  public boolean commitEdit(LongList items, EditCommit commit) {
    return myManager.commitEdit(items, commit);
  }

  @Override
  @Nullable
  public EditorLock findLock(long item) {
    return myManager.findLock(item);
  }

  @Override
  public EditorLock findAnyLock(LongList items) {
    return myManager.findAnyLock(items);
  }

  @Override
  @Nullable
  public EditControl prepareEdit(long item) {
    return myManager.prepareEdit(item);
  }

  @Override
  @Nullable
  public EditControl prepareEdit(LongList items) {
    return myManager.prepareEdit(items);
  }

  @Override
  public void unsafeCommitEdit(EditCommit commit) {
    myManager.unsafeCommitEdit(commit);
  }

  @Override
  public Lifespan upload(long item, ItemUploader uploader) {
    return myManager.upload(item, uploader);
  }

  @Override
  public Lifespan upload(LongList items, ItemUploader uploader) {
    return myManager.upload(items, uploader);
  }

  @Override
  public DBResult<Object> writeDownloaded(DownloadProcedure<? super DBDrain> procedure) {
    return myManager.writeDownloaded(procedure);
  }

  @Override
  public Modifiable getModifiable() {
    return myManager.getModifiable();
  }
}
