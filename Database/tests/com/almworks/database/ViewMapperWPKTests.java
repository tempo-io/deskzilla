package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.util.ViewMapperWithPermanentKey;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.InstanceProvider;

public class ViewMapperWPKTests extends WorkspaceFixture {
  private ViewMapperWithPermanentKey<String> myMapper;
  private int cgid;

  protected void setUp() throws Exception {
    super.setUp();
    Filter filter = myWorkspace.getFilterManager().attributeSet(myAttributeOne, true);
    ArtifactView view = myWorkspace.getViews().getUserView().filter(filter);
    myMapper = ViewMapperWithPermanentKey.create(view, myAttributeOne, String.class);
    Context.add(InstanceProvider.instance(myWorkspace, Workspace.ROLE), null);
    cgid = Context.globalize();
  }

  protected void tearDown() throws Exception {
    Context.unglobalize(cgid);
    Context.pop();
    myMapper.stop();
    myMapper = null;
    super.tearDown();
  }

  public void testSureNegative() throws InterruptedException {
    Revision r1 = createObjectAndSetValue(myAttributeOne, "1").getLastRevision();
    start();
    Revision r2 = createObjectAndSetValue(myAttributeOne, "2").getLastRevision();
    Thread.sleep(500);

    assertEquals(0, myMapper.getRequestCountAheadOfTransaction());
    assertEquals(r1, myMapper.getRevisionLong("1"));
    assertEquals(r2, myMapper.getRevisionLong("2"));
    assertNull(myMapper.getRevisionLong("3"));
    assertEquals(0, myMapper.getRequestCountAheadOfTransaction());
  }

  private void start() throws InterruptedException {
    myMapper.start();
    myMapper.waitInitialized();
  }
}