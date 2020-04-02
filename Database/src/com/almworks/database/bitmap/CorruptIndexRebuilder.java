package com.almworks.database.bitmap;

import com.almworks.database.AbstractArtifactView;
import com.almworks.database.Basis;
import com.almworks.util.commons.Condition;
import com.almworks.util.commons.Lazy;
import com.almworks.util.threads.Threads;
import util.concurrent.Synchronized;

class CorruptIndexRebuilder {
  private final Synchronized<Thread> myRebuilding = new Synchronized<Thread>(null);
  private final Basis myBasis;
  private final Lazy<?> myIndex;
  private final AbstractArtifactView myParent;

  public CorruptIndexRebuilder(Basis basis, Lazy<?> index, AbstractArtifactView parent) {
    myBasis = basis;
    myIndex = index;
    myParent = parent;
  }

  public void rebuildCorruptIndexes() throws InterruptedException {
    Threads.assertLongOperationsAllowed();
    boolean iamRebuilding = myRebuilding.commit(null, Thread.currentThread());
    if (!iamRebuilding) {
      myRebuilding.waitForCondition(Condition.<Thread>isNull());
      return;
    } else {
      try {
        BitmapIndexManager manager = myBasis.getBitmapIndexManager();
        manager.lockRead();
        try {
          Object index;
          synchronized (myIndex.getLock()) {
            if (!myIndex.isInitialized())
              return;
            index = myIndex.get();
          }

          rebuildIndex(index);
          rebuildParentIndexes();

        } finally {
          manager.unlockRead();
        }
      } finally {
        myRebuilding.set(null);
      }
    }
  }

  private void rebuildParentIndexes() throws InterruptedException {
    if (myParent != null && (myParent instanceof ViewHavingBitmap))
      ((ViewHavingBitmap) myParent).rebuildCorruptIndexes();
  }

  private void rebuildIndex(Object index) throws InterruptedException {
    if (index instanceof CompositeBitmapIndex)
      ((CompositeBitmapIndex) index).rebuildCorruptIndexes(myBasis.getBitmapIndexManager());
    else if (index instanceof AbstractBitmapIndex)
      myBasis.getBitmapIndexManager().rebuildIndex(((AbstractBitmapIndex) index));
    else
      assert false : index;
  }
}
