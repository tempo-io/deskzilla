package com.almworks.items.sync.edit;

import com.almworks.integers.*;
import com.almworks.items.api.*;
import com.almworks.items.sync.*;
import com.almworks.items.sync.impl.HolderCache;
import com.almworks.items.sync.impl.SyncSchema;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.AttributeMap;
import com.almworks.util.collections.LongSet;
import com.almworks.util.commons.Procedure;
import com.almworks.util.exec.ThreadGate;
import org.almworks.util.Log;
import org.almworks.util.detach.DetachComposite;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

class UploadTask implements WriteTransaction<Object>, Procedure<DBResult<?>>, ItemUploader.UploadProcess {
  private final LongSet myItems = new LongSet();
  private final SyncManagerImpl myManager;
  private final ItemUploader myUploader;
  private final DetachComposite myUploadLifespan = new DetachComposite();

  UploadTask(SyncManagerImpl manager, ItemUploader uploader, LongList items) {
    myManager = manager;
    myUploader = uploader;
    myItems.addAll(items);
  }
  
  public Lifespan start() {
    if (myItems.isEmpty()) return null;
    if (!getUploadLocks().registerTask(this, myItems)) return null;
    boolean success = false;
    try {
      myManager.enquireWrite(this).finallyDoWithResult(ThreadGate.STRAIGHT, this);
      success = true;
    } finally {
      if (!success) getUploadLocks().unregisterTask(this);
    }
    return myUploadLifespan;
  }

  private UploadLocks getUploadLocks() {
    return myManager.getUploadLocks();
  }

  @Override
  public Object transaction(DBWriter writer) throws DBOperationCancelledException {
    PrepareUpload prepare = new PrepareUpload(writer);
    long[] items = myItems.toNativeArray();
    prepare.perform(items);
    if (!getUploadLocks().isRegistered(this)) return null;
    prepare.writeUploadTasks();
    return null;
  }

  @Override
  public void invoke(DBResult<?> result) {
    boolean success = false;
    try {
      long[] items = getUploadLocks().getLocked(this);
      boolean successful = result.isSuccessful();
      if (items.length == 0 || !successful) return;
      ThreadGate.LONG_QUEUED.execute(new Runnable() {
        @Override
        public void run() {
          boolean success = false;
          try {
            myUploader.doUpload(UploadTask.this);
            success = true;
          } finally {
            if (!success) getUploadLocks().unregisterTask(UploadTask.this);
            myUploadLifespan.detach();
          }
        }
      });
      success = true;
    } finally {
      if (!success) {
        getUploadLocks().unregisterTask(this);
        ThreadGate.LONG_QUEUED.execute(new Runnable() { public void run() {
          myUploadLifespan.detach();
        }});
      }
    }
  }

  @Override
  public DBResult<Object> writeUploadState(DownloadProcedure<? super UploadDrain> procedure) {
    return new UploadDrainImpl(procedure).start();
  }

  @Override
  public void uploadDone() {
    priCancelUpload(null);
  }

  @Override
  public void cancelUpload(long item) {
    cancelUpload(LongArray.singleton(item));
  }

  @Override
  public void cancelUpload(LongList items) {
    priCancelUpload(items);
  }

  /**
   * Cancel upload
   * @param items to cancel upload, null means cancel the whole upload (all locked items)
   */
  private void priCancelUpload(LongList items) {
    final long[] itemsArray = items != null ? items.toNativeArray() : null;
    writeUploadState(new DownloadProcedure<UploadDrain>() {
      @Override
      public void write(UploadDrain drain) throws DBOperationCancelledException {
        long[] toCancel = itemsArray != null ? itemsArray : getUploadLocks().getLocked(UploadTask.this);
        for (long item : toCancel) {
          drain.cancelUpload(item);
        }
      }

      @Override
      public void onFinished(DBResult<?> result) {
        if (!result.isSuccessful()) {
          Log.error("Failed to cancel upload. Items are still locked");
        }
      }
    });
  }

  private void onUploadDone(LongList items) {
    getUploadLocks().unregister(this, items);
  }

  private class UploadDrainImpl extends BaseDownloadDrain implements UploadDrain {
    private final LongSet myDownloaded = new LongSet();
    private final DownloadProcedure<? super UploadDrain> myProcedure;

    public UploadDrainImpl(DownloadProcedure<? super UploadDrain> procedure) {
      super(myManager);
      myProcedure = procedure;
    }

    @Override
    public ItemVersionCreator setAllDone(long item) {
      AttributeMap task = HolderCache.instance(getReader()).getUploadTask(item);
      if (task != null) finishUpload(item, task);
      ItemVersionCreator creator = changeItem(item);
      creator.setValue(SyncSchema.UPLOAD_FAILED, null);
      return creator;
    }

    @Override
    protected void collectToMerge(LongCollector target) {
      super.collectToMerge(target);
      target.addAll(myDownloaded);
    }

    private boolean finishUpload(long item, @Nullable AttributeMap done) {
      boolean inProgress = getUploadLocks().isLocked(UploadTask.this, item);
      if (inProgress) {
        HolderCache holders = HolderCache.instance(getReader());
        AttributeMap task = holders.getUploadTask(item);
        if (task != null) {
          holders.setDoneUpload(item, done);
          holders.setUploadTask(item, null);
        } else inProgress = false;
        myDownloaded.add(item);
      }
      return inProgress;
    }

    @Override
    public void cancelUpload(long item) {
      if (!finishUpload(item, null)) return;
      HolderCache holders = HolderCache.instance(getReader());
      AttributeMap base = holders.getBase(item);
      if (base != null && SyncSchema.isInvisible(base)) changeItem(item).setValue(SyncSchema.UPLOAD_FAILED, true);
    }

    @Override
    public LongList getLockedForUpload() {
      long[] items = getUploadLocks().getLocked(UploadTask.this);
      if (items.length <= 0) return LongList.EMPTY;
      LongSet result = LongSet.create(items);
      result.removeAll(myDownloaded);
      return result;
    }

    @Override
    protected void performTransaction() {
      myProcedure.write(this);
    }

    @Override
    protected void onTransactionFinished(DBResult<?> result) {
      if (result.isSuccessful()) UploadTask.this.onUploadDone(myDownloaded);
      myProcedure.onFinished(result);
    }
  }

  private class PrepareUpload implements ItemUploader.UploadPrepare {
    private final DBWriter myWriter;
    private final LongSet myToUpload = new LongSet();
    private final boolean myTasksWriten = false;

    public PrepareUpload(DBWriter writer) {
      myWriter = writer;
    }

    public void perform(long[] items) {
      for (long item : items) {
        if (myToUpload.contains(item)) continue;
        if (canUpload(item)) prepareItem(item);
        else getUploadLocks().unregister(UploadTask.this, LongArray.singleton(item));
      }
    }

    private void prepareItem(long item) {
      if (myToUpload.contains(item)) return;
      HolderCache holders = HolderCache.instance(myWriter);
      boolean hasBase = holders.hasBase(item);
      if (hasBase) myToUpload.add(item);
      else getUploadLocks().unregister(UploadTask.this, LongArray.singleton(item));
      myUploader.prepare(this, SyncUtils.readTrunk(myWriter, item), hasBase);
    }


    @Override
    public boolean addToUpload(long item) {
      return addToUpload(LongArray.create(item));
    }

    @Override
    public boolean addToUpload(LongList items) {
      if (!getUploadLocks().registerTask(UploadTask.this, items)) return false;
      for (int i = 0; i < items.size(); i++) if (!canUpload(items.get(i))) return false;
      for (int i = 0; i < items.size(); i++) prepareItem(items.get(i));
      return true;
    }

    @Override
    public void cancelUpload() {
      if (myTasksWriten) UploadTask.this.uploadDone();
      else {
        getUploadLocks().unregisterTask(UploadTask.this);
        myToUpload.clear();
      }
    }

    @Override
    public void removeFromUpload(long item) {
      if (myTasksWriten) UploadTask.this.cancelUpload(item);
      else {
        myToUpload.remove(item);
        getUploadLocks().unregister(UploadTask.this, LongArray.singleton(item));
      }
    }

    private boolean canUpload(long item) {
      if (SyncUtils.isRemoved(SyncUtils.readTrunk(myWriter, item))) return false;
      HolderCache holders = HolderCache.instance(myWriter);
      if (holders.hasUploadTask(item)) return false;
      if (AutoMerge.needsMerge(myWriter, item)) {
        if (!myManager.autoMergeNow(myWriter, LongArray.create(item), null) || AutoMerge.needsMerge(myWriter, item)) {
          Log.warn("Cannot upload, merge is required for " + item);
          return false;
        }
      }
      return !holders.hasDoneUpload(item);
    }

    public void writeUploadTasks() {
      HolderCache holders = HolderCache.instance(myWriter);
      for (int i = 0; i < myToUpload.size(); i++) {
        long item = myToUpload.get(i);
        if (!canUpload(item)) Log.error("Cannot upload " + item);
        else holders.setUploadTask(item, SyncUtils.readTrunk(myWriter, item).getAllShadowableMap());
      }
    }
  }
}
