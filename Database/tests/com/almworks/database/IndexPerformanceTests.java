package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.util.Initializer;
import com.almworks.api.database.util.WorkspaceUtils;
import com.almworks.misc.TestWorkArea;
import com.almworks.universe.FileUniverse;
import com.almworks.util.exec.*;
import com.almworks.util.tests.BaseTestCase;
import junit.framework.AssertionFailedError;

public class IndexPerformanceTests extends BaseTestCase {
  private TestWorkArea myArea;
  private FileUniverse myUniverse;
  private Basis myBasis;
  private WorkspaceImpl myWorkspace;

  public IndexPerformanceTests() {
    super(600000);
  }

  protected void setUp() throws Exception {
    WorkspaceStatic.cleanup();
    myArea = new TestWorkArea();
    myUniverse = new FileUniverse(myArea.getDatabaseDir());
    Context.add(InstanceProvider.instance(new LongEventQueueImpl()), "x");
    myBasis = new Basis(myUniverse, ConsistencyWrapper.FAKE);
    myBasis.start();
    myWorkspace = new WorkspaceImpl(myBasis);
    myUniverse.start();
    myWorkspace.repair();
    myWorkspace.open();
  }

  protected void tearDown() throws Exception {
    myWorkspace.stop();
    myWorkspace = null;
    myUniverse.stop();
    myUniverse = null;
    myBasis = null;
    myArea.cleanUp();
    LongEventQueue.instance().shutdownGracefully();
    Context.pop();
  }

  /**
   * We're testing that index search does not take time proportionally or more to the number of elements.
   */
  public void testIndexSearchGrowthWithData() {
    if (true)
      return;
    try {
      doTestIndexSearchGrowthWithData();
    } catch (AssertionFailedError e) {
      // once more!
      doTestIndexSearchGrowthWithData();
    }
  }
  
  public void doTestIndexSearchGrowthWithData() {
    final int CHUNK_SIZE = 10;
    final int CYCLE_COUNT = 100;
    final int BREAK_POINT = CYCLE_COUNT / 2;
    final int MEASUREMENT_COUNT = 10;
    int counter = 0;
    long measurements[] = {0, 0, 0};
    assert MEASUREMENT_COUNT * 5 <= CYCLE_COUNT;
    ArtifactView userView = myWorkspace.getViews().getUserView();
    for (int i = 0; i < CYCLE_COUNT; i++) {
      long start = System.currentTimeMillis();
      for (int j = 0; j < CHUNK_SIZE; j++) {
        Transaction transaction = myWorkspace.beginTransaction();
        final String id = "test:" + Integer.toString(++counter);
        WorkspaceUtils.singularGet(transaction, userView, myWorkspace.getFilterManager(), SystemObjects.ATTRIBUTE.ID, id,
          new Initializer() {
            public void initialize(RevisionCreator creator) {
              creator.setValue(SystemObjects.ATTRIBUTE.ID, id);
            }
          });
        transaction.commitUnsafe();
      }
      long duration = System.currentTimeMillis() - start;
      if (i < MEASUREMENT_COUNT) {
        measurements[0] += duration;
      } else if (i >= BREAK_POINT && i < BREAK_POINT + MEASUREMENT_COUNT) {
        measurements[1] += duration;
      } else if (i == BREAK_POINT + MEASUREMENT_COUNT) {
        assertTrue("m[1] = " + measurements[1] + " > (m[0] = " + measurements[0] + ") * 2",
          measurements[1] <= measurements[0] * 2);
      } else if (i >= CYCLE_COUNT - MEASUREMENT_COUNT) {
        measurements[2] += duration;
      }
    }
    assertTrue("m[2] = " + measurements[2] + " > (m[0] = " + measurements[0] + ") * 3",
      measurements[2] <= measurements[0] * 3);
  }

  public void runTestIndexSearchGrowthWithData() {
    final int CHUNK_SIZE = 10;
    final int CYCLE_COUNT = 200;
    final int BREAK_POINT = CYCLE_COUNT / 2;
    final int MEASUREMENT_COUNT = 10;
    int counter = 0;
    long measurements[] = {0, 0, 0};
    for (int i = 0; i < CYCLE_COUNT; i++) {
      long start = System.currentTimeMillis();
      for (int j = 0; j < CHUNK_SIZE; j++) {
        Transaction transaction = myWorkspace.beginTransaction();
        final String id = "test:" + Integer.toString(++counter);
        WorkspaceUtils.singularGet(transaction, myWorkspace.getViews().getUserView(), myWorkspace.getFilterManager(),
          SystemObjects.ATTRIBUTE.ID, id, new Initializer() {
            public void initialize(RevisionCreator creator) {
              creator.setValue(SystemObjects.ATTRIBUTE.ID, id);
            }
          });
        transaction.commitUnsafe();
      }
      long duration = System.currentTimeMillis() - start;
      System.out.println(i + " " + duration);
    }
  }
}
