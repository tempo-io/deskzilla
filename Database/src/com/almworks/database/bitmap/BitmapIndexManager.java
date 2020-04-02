package com.almworks.database.bitmap;

import com.almworks.api.database.*;
import com.almworks.api.misc.WorkArea;
import com.almworks.database.Basis;
import com.almworks.database.filter.FilterType;
import com.almworks.database.filter.SystemFilter;
import com.almworks.util.progress.Progress;
import org.almworks.util.*;
import util.concurrent.*;

import java.util.*;

public class BitmapIndexManager {
  private final Basis myBasis;
  private final Map<IndexKey, AbstractBitmapIndex> myIndexes = Collections15.hashMap();

  private final SystemFilter LAST_REVISION_DUMMY_FILTER;

  private final Object myGetLock = new Object();
  private final Object myBuildLock = new Object();

  // must be reentrant!
  private final ReadWriteLock myUpdateLock = new ReentrantWriterPreferenceReadWriteLock();

  private final BitmapIndexFileManager myFileManager;

  public BitmapIndexManager(Basis basis, WorkArea workArea) {
    myBasis = basis;
    LAST_REVISION_DUMMY_FILTER = new SystemFilter(basis) {
      public boolean isComposite() {
        return false;
      }

      public FilterType getType() {
        return null;
      }

      public Filter[] getChildren() {
        return null;
      }

      public String getPersistableKey() {
        return "LastRevision";
      }

      public boolean accept(Revision revision) {
        return false;
      }

      public boolean isIndexable() {
        return true;
      }
    };

    myFileManager = workArea == null ? null : new BitmapIndexFileManager(workArea, myUpdateLock);
  }

  public AbstractBitmapIndex getBitmapIndex(SystemFilter systemFilter, RevisionAccess strategy) {
    assert systemFilter == LAST_REVISION_DUMMY_FILTER || systemFilter.getType() == FilterType.LEAF;
    IndexKey key = new IndexKey(systemFilter.getPersistableKey(), strategy);

    AbstractBitmapIndex index = getIndex(key);
    if (index != null)
      return index;

    // access read lock so transaction won't interfere in loading
    Sync sync = myUpdateLock.readLock();
    try {
      sync.acquire();
      try {
        // maybe need to build
        synchronized (myBuildLock) {
          index = getIndex(key);
          if (index != null)
            return index;
          index = createUnrolledIndex(key, systemFilter, strategy);
          setIndex(key, index);
          rollForward(index);
          return index;
        }
      } finally {
        sync.release();
      }
    } catch (InterruptedException e) {
      throw interrupted(e);
    }
  }


  public boolean hasBitmapIndex(SystemFilter systemFilter, RevisionAccess strategy) {
    assert systemFilter == LAST_REVISION_DUMMY_FILTER || systemFilter.getType() == FilterType.LEAF;
    if (systemFilter == LAST_REVISION_DUMMY_FILTER)
      return true;
    IndexKey key = new IndexKey(systemFilter.getPersistableKey(), strategy);
    AbstractBitmapIndex index = getIndex(key);
    if (index != null)
      return true;
    return myFileManager.hasIndex(key);
  }


  AbstractBitmapIndex createUnrolledIndex(IndexKey key, SystemFilter systemFilter, RevisionAccess strategy) {
    AbstractBitmapIndex index;
    if (systemFilter == LAST_REVISION_DUMMY_FILTER)
      index = new LastRevisionBitmapIndex(strategy, systemFilter, myUpdateLock, myBasis);
    else
      index = new FilterBitmapIndex(strategy, systemFilter, myUpdateLock, myBasis);

    if (myFileManager != null) {
      BitmapIndexInfo indexInfo = myFileManager.loadIndex(key);
      if (indexInfo != null)
        index.loadData(indexInfo);
    }
    return index;
  }

  private void rollForward(AbstractBitmapIndex index) {
    index.rollForward();
    saveDirtyIndex(index);
  }

  private void saveDirtyIndex(AbstractBitmapIndex index) {
    if (myFileManager != null && index.isDirty())
      myFileManager.saveIndex(index);
  }

  private void setIndex(IndexKey key, AbstractBitmapIndex index) {
    synchronized (myGetLock) {
      myIndexes.put(key, index);
    }
  }

  private AbstractBitmapIndex getIndex(IndexKey key) {
    synchronized (myGetLock) {
      return myIndexes.get(key);
    }
  }

  public AbstractBitmapIndex getLastRevisionIndex(RevisionAccess strategy) {
    return getBitmapIndex(LAST_REVISION_DUMMY_FILTER, strategy);
  }

  public void start() {
    if (myFileManager != null)
      myFileManager.start();
    myBasis.ourTransactionControl.addListener(new TransactionListener.Adapter() {
      public void onNewRevisionsAppeared(Transaction transaction, Collection<Revision> newRevisions) {
        process(newRevisions, transaction.getCommitWCN());
      }
    });
  }

  private void process(Collection<Revision> revisions, WCN commitWCN) {
    assert commitWCN != null;
    try {
      AbstractBitmapIndex[] indexes = getIndexes(); // must not come under update lock!

      Sync sync = myUpdateLock.writeLock();
      sync.acquire();
      try {
        for (Iterator<Revision> jj = revisions.iterator(); jj.hasNext();) {
          Revision revision = jj.next();
          for (int i = 0; i < indexes.length; i++) {
            indexes[i].updateIndex(revision);
          }
        }
        for (int i = 0; i < indexes.length; i++) {
          AbstractBitmapIndex index = indexes[i];
          index.updateWCN(commitWCN);
          saveDirtyIndex(index);
        }
      } finally {
        sync.release();
      }
    } catch (InterruptedException e) {
      throw interrupted(e);
    }
  }

  public AbstractBitmapIndex[] getIndexes() {
    synchronized (myGetLock) {
      return myIndexes.values().toArray(new AbstractBitmapIndex[myIndexes.values().size()]);
    }
  }

  private RuntimeInterruptedException interrupted(InterruptedException e) {
    Log.warn(this + " interrupted", e);
    return new RuntimeInterruptedException(e);
  }


  public void lockRead() throws InterruptedException {
    myUpdateLock.readLock().acquire();
  }

  public void unlockRead() {
    myUpdateLock.readLock().release();
  }

  public void rebuildIndex(AbstractBitmapIndex index) throws InterruptedException {
    Sync sync = myUpdateLock.readLock();
    sync.acquire();
    try {
      doRebuildIndex(index);
    } finally {
      sync.release();
    }
  }

  private void doRebuildIndex(AbstractBitmapIndex index) {
    synchronized (index) {
      Log.debug("rebuilding index " + index.getKey());
      index.drop();
      saveDirtyIndex(index);
      rollForward(index);
    }
  }

  public void dropAllIndexes(Progress progressSink) throws InterruptedException {
    progressSink.setProgress(0F, "Dropping database indexes");
    Sync sync = myUpdateLock.writeLock();
    sync.acquire();
    try {
      progressSink.setProgress(0.01F, "Deleting index file");
      myFileManager.dropIndexFiles();
      progressSink.setProgress(0.1F, "Rebuilding indexes");
      int count = myIndexes.size();
      if (count > 0) {
        double step = 0.9F / count;
        int i = 0;
        for (AbstractBitmapIndex index : myIndexes.values()) {
          doRebuildIndex(index);
          progressSink.setProgress(0.1F + (float)(step * (++i)), "Rebuilding indexes");
        }
      }
    } finally {
      sync.release();
    }
  }
}
