package com.almworks.database.bitmap;

import com.almworks.api.database.*;
import com.almworks.database.*;
import com.almworks.database.filter.SystemFilter;
import com.almworks.util.exec.ThreadGate;
import util.concurrent.CountDown;
import util.concurrent.SynchronizedBoolean;

public class BitmapIndexUnsyncTests extends WorkspaceFixture {
  private static final int PAUSE = 400;

  protected Basis createBasis() {
    return new Basis(myUniverse, ConsistencyWrapper.FAKE) {
      protected BitmapIndexManager createBitmapIndexManager() {
        return new BitmapIndexManager(this, myWorkArea) {
          AbstractBitmapIndex createUnrolledIndex(IndexKey key, SystemFilter systemFilter, RevisionAccess strategy) {
            AbstractBitmapIndex index = super.createUnrolledIndex(key, systemFilter, strategy);
            try {
              Thread.sleep(PAUSE * 2);
            } catch (InterruptedException e) {
            }
            return index;
          }
        };
      }
    };
  }

  /**
   * Problem timeline:
   * 1. T: transaction starts
   * 2. I: index creation starts
   * 3. I: index is loaded
   * 4. I: index is being rolled forward
   * 5. T: transaction finishes, notifying everyone including index manager
   * 6. T: indexes access updated, but the one that is being created is not updated
   * 7. I: index loading finishes and index is added to the pool
   * <p/>
   * Result: index loses transaction.
   * <p/>
   * Additionally, rollForward() method asks myBasis.getCurrentWCN(), which is *late*
   * wcn - meaning that all transaction hooks must finish before wcn is advanced.
   */
  public void testUnsyncBetweenTransactionAndIndexCreation() throws InterruptedException {
    // 1. Create some data
    Transaction t = myWorkspace.beginTransaction();
    RevisionCreator creator = t.createArtifact();
    creator.setValue(myAttributeOne, "i");
    t.commitUnsafe();

    final ArtifactView[] view = new ArtifactView[1];
    final ArtifactView rootView = myWorkspace.getViews().getRootView();
    final FilterManager fm = myWorkspace.getFilterManager();
    final SynchronizedBoolean viewCreated = new SynchronizedBoolean(false);

    final CountDown countDown = new CountDown(1);
    // 2. start creating view
    ThreadGate.NEW_THREAD.execute(new Runnable() {
      public void run() {
        Filter filter = fm.attributeSet(myAttributeOne, true);
        view[0] = rootView.filter(filter);
        // start creating the artifact
        countDown.release();
        // force index creation
        int c = view[0].count();
        viewCreated.set(true);
      }
    });

    // 3. in parallel, add artifacts to the views
    countDown.acquire();
    t = myWorkspace.beginTransaction();
    creator = t.createArtifact();
    creator.setValue(myAttributeOne, "x");
    Thread.sleep(PAUSE);
    t.commitUnsafe();
    viewCreated.waitForValue(true);

    // 4. now check that all views contain the correct number of all artifacts
    assertEquals(2, view[0].count());
  }
}
