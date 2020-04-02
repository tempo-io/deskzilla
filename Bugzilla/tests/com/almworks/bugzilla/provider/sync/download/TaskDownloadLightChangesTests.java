package com.almworks.bugzilla.provider.sync.download;

import com.almworks.api.engine.SyncType;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.provider.sync.TaskTestFixture;
import com.almworks.integers.IntList;
import com.almworks.util.tests.CollectionsCompare;

/**
 * :todoc:
 *
 * @author sereda
 */
public class TaskDownloadLightChangesTests extends TaskTestFixture {
  private TaskDownload myTask;
  private static final Integer THREE = 3;
  private static final Integer FOUR = 4;
  private static final Integer FIVE = 5;
  private static final Integer SIX = 6;

  protected void setUp() throws Exception {
    super.setUp();
    myTask = new TaskDownload(myController);
  }

  protected void tearDown() throws Exception {
    myTask = null;
    super.tearDown();
  }

  public void testGetBugIDsRequiringLightInfo() {
    myData.updateBox(THREE, SyncType.RECEIVE_AND_SEND);
    myData.updateBox(FOUR, SyncType.RECEIVE_AND_SEND);
    myData.updateBox(FIVE, SyncType.RECEIVE_AND_SEND);
    myData.updateBox(SIX, SyncType.RECEIVE_AND_SEND);
    myData.getBugBox(THREE).setError(BugInfo.ErrorType.BUG_ACCESS_DENIED);
    myData.getBugBox(FIVE).setInfoLight(new BugInfo(null));

    IntList ids = myTask.getBugIds(false);
    new CollectionsCompare().unordered(ids.toList(), new Integer[] {FOUR, SIX});
  }
}
