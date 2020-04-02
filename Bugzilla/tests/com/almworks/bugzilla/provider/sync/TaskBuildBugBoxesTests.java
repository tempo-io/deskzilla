package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.engine.SyncParameters;
import com.almworks.api.engine.SyncType;
import com.almworks.bugzilla.provider.BugzillaAccessPurpose;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.sync.*;
import com.almworks.items.util.SyncAttributes;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import org.almworks.util.Collections15;

/**
 * :todoc:
 *
 * @author sereda
 */
public class TaskBuildBugBoxesTests extends TaskTestFixture {
  private TaskBuildBugBoxes myTask;
  public static DBAttribute<String> attrOne = DBAttribute.String("one", "one");
  static {
    SyncAttributes.initShadowable(attrOne);
  }

  protected void setUp() throws Exception {
    super.setUp();
    myTask = new TaskBuildBugBoxes(myController);
  }

  protected void tearDown() throws Exception {
    myTask = null;
    super.tearDown();
  }

  public void testAddingPendingBugs() {
    myPendingBugs = Collections15.hashMap();
    myPendingBugs.put(1, SyncType.RECEIVE_AND_SEND);
    myPendingBugs.put(2, SyncType.RECEIVE_FULL);
    myPendingBugs.put(3, SyncType.RECEIVE_ONLY);
    myTask.addPendingBugs();
    assertEquals(SyncType.RECEIVE_AND_SEND, myData.getBugBox(1).getSyncType());
    assertEquals(SyncType.RECEIVE_FULL, myData.getBugBox(2).getSyncType());
    assertEquals(SyncType.RECEIVE_ONLY, myData.getBugBox(3).getSyncType());
    assertNull(myData.getBugBox(null));
    assertEquals(3, myData.getBugBoxes().size());

    myPendingBugs = null;
    myTask.addPendingBugs();
    assertEquals(3, myData.getBugBoxes().size());
  }

  public void testDetectBugsRequestedForSync() throws ConnectionNotConfiguredException, InterruptedException, CancelledException {
    final long[] createdItem = new long[1];
    commitAndWait(new EditCommit.Adapter() {
      @Override
      public void performCommit(EditDrain drain) throws DBOperationCancelledException {
        ItemVersionCreator creator = createEmptyBug(drain);
        creator.setValue(Bug.attrBugID, 4);
        creator.setValue(attrOne, "xxx");
        createdItem[0] = creator.getItem();
      }
    });
    reinitData(SyncParameters.synchronizeItem(createdItem[0]));
    myTask.detectBugsRequestedForSync();
    testBoxes(1, 4, SyncType.RECEIVE_AND_SEND);
  }

  private void reinitData(SyncParameters syncParameters) throws ConnectionNotConfiguredException {
    if (syncParameters != null)
      mySyncParameters = syncParameters;
    myData =
      new SyncData(myContext, mySynchroState, myCancelFlag, myLastCommittedWCN, BugzillaAccessPurpose.SYNCHRONIZATION);
  }

  private void testBoxes(int count, int checkID, SyncType checkType) {
    assertEquals(count, myData.getBugBoxes().size());
    if (checkType != null)
      assertEquals(checkType, myData.getBugBox(checkID).getSyncType());
  }
}
