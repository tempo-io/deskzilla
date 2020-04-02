package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.util.HintedArtifactView;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.tests.CollectionsCompare;
import org.almworks.util.RuntimeInterruptedException;
import util.concurrent.CountDown;
import util.concurrent.SynchronizedInt;

import java.util.List;

public class HintedArtifactViewTests extends WorkspaceFixture {
  private static final int COUNT = 10;

  public void testPast() {
    createObjects(1);
    ArtifactView view = createView(1);

    List<Revision> listOne = view.getAllArtifacts();
    assertEquals(COUNT, listOne.size());
    WCN wcn = myBasis.getCurrentWCN();

    HintedArtifactView hinted =
      new HintedArtifactView(listOne.toArray(new Revision[listOne.size()]), wcn.getUCN(), view);

    List<Revision> listTwo = hinted.getAllArtifacts();
    new CollectionsCompare().order(listOne, listTwo);
    assertFalse(hinted.isDisposed());

    createObjects(1);
    listOne = view.getAllArtifacts();
    assertEquals(COUNT * 2, listOne.size());
    listTwo = hinted.getAllArtifacts();
    new CollectionsCompare().order(listOne, listTwo);
    assertTrue(hinted.isDisposed());
  }

  public void testFuture() {
    // known to fail
    // database rewrite!
    if (System.getProperty("is.debugging") == null)
      return;

    checkFuture(1, false);
  }

  private void checkFuture(int p, boolean concurrent) {
    createObjects(p);
    ArtifactView view = createView(p);
    List<Revision> listOne = view.getAllArtifacts();
    assertEquals(COUNT, listOne.size());
    WCN wcn = myBasis.getCurrentWCN();

    HintedArtifactView hinted =
      new HintedArtifactView(listOne.toArray(new Revision[listOne.size()]), wcn.getUCN(), view);

    TestListener listener = new TestListener(5000, "" + p);
    hinted.addListener(ThreadGate.LONG(Thread.currentThread()), WCN.ETERNITY, listener);
    if (!concurrent)
      assertFalse(hinted.isDisposed());

    Artifact created = createObjectAndSetValue(myAttributeOne, "" + p);
    Artifact changed = listOne.get(0).getArtifact();
    Artifact removed = listOne.get(1).getArtifact();
    changeArtifact(changed, RevisionAccess.ACCESS_DEFAULT, myAttributeTwo, "ddd");
    unsetObjectValue(removed, myAttributeOne);

    listener.assertValues(listOne.get(COUNT - 1).getArtifact(), created, removed, changed);
  }

  public void testFutureConcurrently() throws InterruptedException {
    // known to fail
    // database rewrite!
    if (System.getProperty("is.debugging") == null)
      return;

    final int THREADS = 3;
    final CountDown start = new CountDown(THREADS + 1);
    final CountDown stop = new CountDown(THREADS + 1);
    final SynchronizedInt done = new SynchronizedInt(0);
    Context.globalize();
    for (int i = 0; i < THREADS; i++) {
      final int p = i;
      new Thread() {
        public void run() {
          try {
            start.release();
            start.acquire();
            checkFuture(p, true);
            done.increment();
          } catch (InterruptedException e) {
            throw new RuntimeInterruptedException(e);
          } finally {
            stop.release();
          }
        }
      }.start();
    }
    start.release();
    stop.release();
    stop.acquire();
    assertEquals(THREADS, done.get());
  }

  private ArtifactView createView(int i) {
    FilterManager fm = myWorkspace.getFilterManager();
    ArtifactView userView = myWorkspace.getViews().getUserView();
    ArtifactView view = userView.filter(fm.attributeEquals(myAttributeOne, "" + i, true));
    return view;
  }

  private void createObjects(int p) {
    for (int i = 0; i < COUNT; i++) {
      createObjectAndSetValue(myAttributeOne, "" + p);
    }
  }
}
