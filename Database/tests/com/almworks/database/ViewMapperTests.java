package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.util.ViewMapper;

public class ViewMapperTests extends WorkspaceFixture {
  private ViewMapper<String> myMapper;
  private Revision myRevision;

  protected void setUp() throws Exception {
    super.setUp();
    Filter filter = myWorkspace.getFilterManager().attributeSet(myAttributeOne, true);
    ArtifactView view = myWorkspace.getViews().getUserView().filter(filter);
    myMapper = ViewMapper.create(view, myAttributeOne, String.class);
    myRevision = createObjectAndSetValue(myAttributeOne, "A").getLastRevision();
  }

  protected void tearDown() throws Exception {
    myMapper.stop();
    myMapper = null;
    super.tearDown();
  }

  public void testStartStop() throws InterruptedException {
    assertNull(get("A"));
    start();
    assertEquals(myRevision, get("A"));
    assertNull(get("B"));
    myMapper.stop();
//    assertNull(get("A"));
    createObjectAndSetValue(myAttributeOne, "B");
    Revision rev = get("B");
    assertEquals(null, rev);
  }

  public void testBoundaries() throws InterruptedException {
    assertNull(get(null));
    assertNull(get(""));
    start();
    assertNull(get(null));
    assertNull(get(""));
  }

  public void testFuture() throws InterruptedException {
    start();
    assertEquals(myRevision, get("A"));
    Revision rev = changeArtifact(myRevision.getArtifact(), RevisionAccess.ACCESS_DEFAULT, myAttributeTwo, "XYZ")
      .asRevision();
    waitEffect(rev.getWCN());
    sleep(100);
    assertEquals(rev, get("A"));
    assertNull(get("XYZ"));

    deleteObject(myRevision);
    sleep(100);
    assertNull(get("A"));

    rev = createObjectAndSetValue(myAttributeOne, "B").getLastRevision();
    waitEffect(rev.getWCN());
    assertEquals(rev, get("B"));
    assertNull(get("A"));
    rev = changeArtifact(rev.getArtifact(), RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "A").asRevision();
    waitEffect(rev.getWCN());
    assertEquals(rev, get("A"));
    assertNull(get("B"));
  }

  private void waitEffect(WCN wcn) throws InterruptedException {
    // todo remove sleep
    sleep(100);
    myMapper.waitWCN(wcn);
  }

  private void start() throws InterruptedException {
    myMapper.start();
    myMapper.waitInitialized();
  }

  private Revision get(String key) {
    Artifact artifact = myMapper.getRevisionNow(key);
    return artifact == null ? null : artifact.getLastRevision();
  }
}
