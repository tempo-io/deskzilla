package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.util.RangeFilter;
import com.almworks.util.exec.ThreadGate;

/**
 * :todoc:
 *
 * @author sereda
 */
public class FilterTests extends WorkspaceFixture {
  public void testTotalObjectIncrease() {
    int totalObjects = count(myWorkspace.getViews().getRootView(), -1);
    assertTrue(totalObjects > 0);
    createObjectAndSetValue(myAttributeOne, "one");
    count(myWorkspace.getViews().getRootView(), totalObjects + 1);
  }

  public void testListenNewObject() {
    TestListener tester = new TestListener();
    myWorkspace.getViews().getRootView().addListenerFuture(ThreadGate.STRAIGHT, tester);
    Artifact object;
    tester.assertValues(null, null, null, null);
    object = createObjectAndSetValue(myAttributeOne, "one");
    tester.assertValues(null, object, null, null);
    object = createObjectAndSetValue(myAttributeTwo, "two");
    tester.assertValues(null, object, null, null);
  }

  public void testListenObjectUpdate() {
    TestListener tester = new TestListener();
    myWorkspace.getViews().getRootView().addListenerFuture(ThreadGate.STRAIGHT, tester);
    Artifact object;
    object = createObjectAndSetValue(myAttributeOne, "one");
    tester.assertValues(null, object, null, null);
    Artifact modification = setObjectValue(object, myAttributeOne, "two").getArtifact();
    tester.assertValues(null, null, null, modification);
  }

  public void testListenObjectExists() {
    TestListener tester = new TestListener();
    Artifact object;
    object = createObjectAndSetValue(myAttributeOne, "one");
    WCN.Range range = WCN.createRange(object.getWCN(), WCN.LATEST);
    myWorkspace.getViews().getRootView().addListener(ThreadGate.STRAIGHT, range, tester);
    tester.assertValues(object, null, null, null);
    Artifact modification = setObjectValue(object, myAttributeOne, "two").getArtifact();
    tester.assertValues(null, null, null, modification);
  }

  public void testFilteredObjectsCount() {
    FilterManager filterManager = myWorkspace.getFilterManager();
    Filter filter = filterManager.and(
      filterManager.not(filterManager.attributeSet(myWorkspace.getSystemObject(SystemObjects.ATTRIBUTE.DELETED), true)),
      filterManager.attributeEquals(myAttributeOne, "one", true));
    ArtifactView view = myWorkspace.getViews().getRootView().filter(filter);
    int totalAtStart = count(view, -1);

    createObjectAndSetValue(myAttributeOne, "one");
    count(view, totalAtStart + 1);

    createObjectAndSetValue(myAttributeTwo, "one");
    count(view, totalAtStart + 1);

    createObjectAndSetValue(myAttributeOne, "on");
    count(view, totalAtStart + 1);

    Artifact object = createObjectAndSetValue(myAttributeOne, "one");
    count(view, totalAtStart + 2);

    setObjectValue(object, myAttributeTwo, "one");
    count(view, totalAtStart + 2);

    setObjectValue(object, myAttributeOne, "two");
    count(view, totalAtStart + 1);

    setObjectValue(object, myAttributeOne, "one");
    count(view, totalAtStart + 2);

    deleteObject(object);
    count(view, totalAtStart + 1);
  }

  public void testFilteredObjectsEvents() throws Exception {
    FilterManager filterManager = myWorkspace.getFilterManager();
    Filter filter = filterManager.and(
      filterManager.not(filterManager.attributeSet(myWorkspace.getSystemObject(SystemObjects.ATTRIBUTE.DELETED), true)),
      filterManager.attributeEquals(myAttributeOne, "one", true));
    ArtifactView view = myWorkspace.getViews().getRootView().filter(filter);

    Artifact object;
    TestListener tester = new TestListener();

    object = createObjectAndSetValue(myAttributeOne, "one");

    view.addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, tester);
    tester.assertValues(object, null, null, null);

    object = createObjectAndSetValue(myAttributeOne, "one");
    tester.assertValues(null, object, null, null);

    object = setObjectValue(object, myAttributeTwo, "one").getArtifact();
    tester.assertValues(null, null, null, object);

    Object disappearedObject = setObjectValue(object, myAttributeOne, "two");
    tester.assertValues(null, null, object, null);

    object = setObjectValue(object, myAttributeOne, "one").getArtifact();
    tester.assertValues(null, object, null, null);

    deleteObject(object);
    tester.assertValues(null, null, object, null);
  }

  public void testTimeFilteredViews() {
    //WCN wcn = myWorkspace.getWCN();
    Artifact object = createObjectAndSetValue(myAttributeOne, "one");
    ArtifactView view = myWorkspace.getViews().getRootView().filter(
      new RangeFilter(WCN.createRange(object.getWCN(), WCN.LATEST)));
    count(view, 1);
  }

  public void testUserView() throws InterruptedException {
    int N = 2;
    ArtifactView view = myWorkspace.getViews().getUserView();
    count(view, N);
    Artifact object = createObjectAndSetValue(myAttributeOne, "x");
    count(view, N + 1);
    deleteObject(myAttributeTwo);
    count(view, N);

    Filter filter = myWorkspace.getFilterManager().attributeEquals(myAttributeOne, "x", true);
    assertTrue(object.equals(view.queryLatest(filter)));
    deleteObject(object);
    assertTrue(view.queryLatest(filter) == null);
    object = createObjectAndSetValue(myAttributeOne, "x");
    assertTrue(object.equals(view.queryLatest(filter)));
  }

  private int count(ArtifactView view, int shouldBe) {
    int n = view.getAllArtifacts().size();
    assertEquals(n, view.count());
    if (shouldBe >= 0) {
      assertTrue("n = " + n + "; should be " + shouldBe, n == shouldBe);
    }
    return n;
  }

}
