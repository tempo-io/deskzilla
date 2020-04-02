package com.almworks.database.bitmap;

import com.almworks.api.misc.WorkArea;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.Bottleneck;
import org.almworks.util.*;
import util.concurrent.ReadWriteLock;
import util.concurrent.Sync;

import java.io.File;
import java.io.IOException;
import java.util.Set;

class BitmapIndexFileManager {
  private final File myDatabaseDir;
  private final IndexIndexFile myIndexIndexFile;
  private static final String INDEX_PREFIX = "bi.";
  static final String INDEX_INDEX_FILE = "bi.idx";

  private Bottleneck mySave;

  private final Set<AbstractBitmapIndex> myDirtyIndexes = Collections15.hashSet();
  private final ReadWriteLock myUpdateLock;

  public BitmapIndexFileManager(WorkArea workArea, ReadWriteLock updateLock) {
    myUpdateLock = updateLock;
    myDatabaseDir = workArea.getDatabaseDir();
    myIndexIndexFile = new IndexIndexFile(new File(myDatabaseDir, INDEX_INDEX_FILE), myDatabaseDir, INDEX_PREFIX);
  }

  public void start() {
    MIGRATE.migrateToBeta3a(myDatabaseDir);
    MIGRATE.checkDropIndexesSetting(myDatabaseDir);
    mySave = new Bottleneck(10000, ThreadGate.LONG(this), new Runnable() {
      public void run() {
        doSave();
      }
    });
  }


  public BitmapIndexInfo loadIndex(IndexKey key) {
    File indexFile = null;
    try {
      indexFile = myIndexIndexFile.getIndexFileForKey(key.getIndexKey());
      if (!indexFile.isFile())
        return null;
      BitmapIndexFile file = new BitmapIndexFile(indexFile);
      return file.loadIndex();
    } catch (IOException e) {
      Log.warn("cannot load index for " + key + " from " + indexFile, e);
      return null;
    }
  }

  public boolean hasIndex(IndexKey key) {
    try {
      return myIndexIndexFile.hasIndexFileForKey(key.getIndexKey());
    } catch (IOException e) {
      return false;
    }
  }

  public void saveIndex(AbstractBitmapIndex index) {
    synchronized (myDirtyIndexes) {
      myDirtyIndexes.add(index);
    }
    if (mySave != null)
      mySave.run();
  }

  private void doSave() {
    AbstractBitmapIndex[] indexes;
    synchronized (myDirtyIndexes) {
      int size = myDirtyIndexes.size();
      if (size == 0)
        return;
      indexes = myDirtyIndexes.toArray(new AbstractBitmapIndex[size]);
      myDirtyIndexes.clear();
    }

    try {
      Sync sync = myUpdateLock.readLock();
      sync.acquire();
      try {
        for (int i = 0; i < indexes.length; i++) {
          AbstractBitmapIndex index = indexes[i];
          try {
            String key = index.getKey().getIndexKey();
            File indexFile = myIndexIndexFile.getIndexFileForKey(key);
            BitmapIndexFile file = new BitmapIndexFile(indexFile);
            file.saveIndex(index.getAtomBitsInternal(), index.getWCN());
            index.clearDirty();
          } catch (IOException e) {
            Log.warn("failed to write index " + index, e);
          }
        }
      } finally {
        sync.release();
      }
    } catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
  }

  public void dropIndexFiles() {
    MIGRATE.dropIndexes(myDatabaseDir, false);
  }
}
