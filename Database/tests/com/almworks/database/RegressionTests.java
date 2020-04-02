package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;
import com.almworks.api.database.util.*;
import com.almworks.util.Pair;
import com.almworks.util.events.ProcessingLock;
import com.almworks.util.exec.SeparateEventQueueGate;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.tests.CollectionsCompare;
import org.almworks.util.Collections15;
import org.almworks.util.RuntimeInterruptedException;
import org.almworks.util.detach.Detach;
import org.jetbrains.annotations.*;
import util.concurrent.CountDown;
import util.concurrent.SynchronizedBoolean;

import java.util.*;

public class RegressionTests extends WorkspaceFixture implements SystemObjects {
  public void testLastRevisionModelNeverHasNullForExistingArtifact() throws InterruptedException {
    Transaction transaction = createTransaction();
    Revision revision = transaction.createArtifact().asRevision();
    transaction.commitUnsafe();
    long key = revision.getChain().getKey();
    ScalarModel<Revision> model = myBasis.ourRevisionMonitor.getLastRevisionModel(key);
    assertNotNull(model.getValue());

    transaction = createTransaction();
    revision = transaction.createArtifact().asRevision();
    transaction.commitUnsafe();
    final long tkey = revision.getChain().getKey();
    int THREAD_COUNT = 20;
    final CountDown start = new CountDown(THREAD_COUNT + 1);
    final CountDown stop = new CountDown(THREAD_COUNT);
    for (int i = 0; i < THREAD_COUNT; i++) {
      new Thread() {
        public void run() {
          try {
            start.release();
            start.acquire();
            Revision r = myBasis.ourRevisionMonitor.getOrCalculateLastRevision(tkey);
            assertNotNull("got null", r);
          } catch (InterruptedException e) {
            throw new RuntimeInterruptedException(e);
          } finally {
            stop.release();
          }
        }
      }.start();
    }
    start.release();
    stop.acquire();
    Thread.sleep(100);
  }

  public void testSingletonEqualsToArtifactPointer() {
    Singletons ss = new Singletons();
    ss.initialize(myWorkspace);
    ArtifactPointer p = ss.attribute.getArtifact();
    assertEquals(ss.attribute, p);
    Singletons ss2 = new Singletons();
    ss2.initialize(myWorkspace);
    assertEquals(ss2.attribute, p);
    assertEquals(ss2.attribute, ss.attribute);
  }

  /**
   * Test the following problem:
   * When adding listener to the transaction controller, we receive "past" time point,
   * which we may scan, and for the future we'll receive notifications.
   * If adding listener happens concurrently with {@link Transaction#commitUnsafe}, we may access
   * a point in time which includes fresh transaction, and at the same time our listener
   * will be called with that transaction.
   */
  public void testTransactionSubscriptionRaceCondition() throws InterruptedException {
    final int ATTEMPTS = 2;
    for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
      final CountDown go = new CountDown(2);
      final CountDown finished = new CountDown(2);
      final SynchronizedBoolean stop = new SynchronizedBoolean(false);
      final boolean[] success = {true};
      final WCN[] failedWcns = {null, null};
      Thread myCommittingThread = new Thread() {
        public void run() {
          try {
            go.release();
            go.acquire();
            for (int i = 0; i < 10; i++) {
              final int OBJECT_COUNT = 100;
              Transaction transaction = myWorkspace.beginTransaction();
              for (int j = 0; j < OBJECT_COUNT && !stop.get(); j++)
                transaction.createArtifact().setValue(myAttributeOne, "one" + j);
              transaction.commitUnsafe();
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            finished.release();
          }
        }
      };
      Thread myListeningThread = new Thread() {
        public void run() {
          try {
            final int LISTEN_COUNT = 1000;
            go.release();
            go.acquire();
            for (int i = 0; i < LISTEN_COUNT && !stop.get(); i++) {
              final WCN[] wcnHolder = {null};
              Pair<Detach, WCN> info = myWorkspace.addListener(new TransactionListener.Adapter() {
                public void onCommit(Transaction transaction, Set<Artifact> artifacts, ProcessingLock lock) {
                  if (stop.get())
                    return;
                  WCN wcn = wcnHolder[0];
                  if (wcn != null) {
                    WCN transactionWcn = transaction.getCommitWCN();
                    boolean check = !transactionWcn.isEarlier(wcn);
                    if (!check) {
                      stop.set(true);
                      success[0] = false;
                      failedWcns[0] = transactionWcn;
                      failedWcns[1] = wcn;
                    }
                  }
                }
              });
              wcnHolder[0] = info.getSecond();
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            finished.release();
          }
        }
      };
      myCommittingThread.start();
      myListeningThread.start();
      finished.acquire();
      if (!success[0]) {
        fail("transaction wcn " + failedWcns[0] + "; subscribe wcn " + failedWcns[1]);
      }
    }
  }

  public void testGettingDuplicatesFromCachingView() throws InterruptedException {
    String[] values = {"x", "y", "z"};
    for (int i = 0; i < values.length; i++)
      createObjectAndSetValue(myAttributeOne, values[i]);
    ArtifactView myView = myWorkspace.getViews().getRootView().filter(
      myWorkspace.getFilterManager().attributeSet(myAttributeOne, true));
    final List<String> result = Collections15.arrayList();
    myView.addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, new ArtifactListener.Adapter() {
      public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
        result.add(lastRevision.getValue(myAttributeOne).getValue(String.class));
        return true;
      }
    });
    new CollectionsCompare().unordered(result, values);
  }

  public void testGettingOneObjectSeveralTimesFromCachingView() {
    final Artifact object = createObjectAndSetValue(myAttributeOne, "x");
    ArtifactView myView = myWorkspace.getViews().getRootView().filter(
      myWorkspace.getFilterManager().attributeSet(myAttributeOne, true));
    final int[] count = {0, 0};
    myView.addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, new ArtifactListener.Adapter() {
      public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
        assertEquals(object, artifact);
        count[0]++;
        return true;
      }

      public boolean onArtifactAppears(Artifact artifact, Revision lastRevision) {
        onArtifactExists(artifact, lastRevision);
        return true;
      }

      public boolean onArtifactChanges(Artifact artifact, Revision prevRevision, Revision newRevision) {
        assertEquals(object, artifact);
        count[1]++;
        return true;
      }
    });
    assertEquals(1, count[0]);
    assertEquals(0, count[1]);
    setObjectValue(object, myAttributeOne, "y");
    assertEquals(1, count[0]);
    assertEquals(1, count[1]);
  }

  /**
   * see #918
   */
  public void testParentArtifactViewDoesNotFireAppearsAfterDisappears() {
    Artifact A = createRCB("1");
    changeArtifact(A, RevisionAccess.ACCESS_MAINCHAIN, myAttributeTwo, "2");
    ArtifactView userView = myWorkspace.getViews().getUserView();
    FilterManager fm = myWorkspace.getFilterManager();
    ArtifactView parentView = userView.filter(fm.attributeEquals(myAttributeOne, "1", true));
    ArtifactView childView = parentView.filter(fm.attributeEquals(myAttributeTwo, "2", true));
    Collection<Revision> all = childView.getAllArtifacts();
    assertEquals(1, all.size());
    assertEquals(A, all.iterator().next().getArtifact());

    TestListener test = new TestListener();
    childView.addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, test);
    test.clear();

    // now locally change A so it does not satisfy parent view
    changeArtifact(A, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "-1");
    test.assertValues(null, null, A, null);

    // now discard local changes and revert to the old version
    A.getRCBExtension(true).closeLocalChain(myWorkspace);
    test.assertValues(null, A, null, null);
  }

  public void testReincarnationDoesNotFireDisappear() throws CollisionException {
    // see #1034
    Artifact A = createRCB("1");
    Filter.Equals filter = myWorkspace.getFilterManager().attributeEquals(myAttributeOne, "1", true);
    ArtifactView view = myWorkspace.getViews().getUserView().filter(filter);
    TestListener tester = new TestListener();
    view.addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, tester);
    tester.assertValues(A, null, null, null);
    RCBArtifact rcb = A.getRCBExtension(true);
    Transaction transaction = myWorkspace.beginTransaction();
    RevisionCreator creator = rcb.startReincarnation(transaction);
    creator.setValue(myAttributeOne, "2");
    transaction.commit();
    rcb.finishReincarnation(myWorkspace);
    tester.assertValues(null, null, A, null);
  }

  public void testConsequentChangesToArtifactConsumedByArtifactView() throws InterruptedException {
//    if (true)
//      return;


    // the problem was that the database may be changed by the second transaction before all views finish
    // procesing the first.
    Artifact A = createObjectAndSetValue(myAttributeOne, "1");

    FilterManager fm = myWorkspace.getFilterManager();
    ArtifactView view = myWorkspace.getViews().getUserView().filter(fm.attributeEquals(myAttributeOne, "1", true));

    final SeparateEventQueueGate queue = new SeparateEventQueueGate(null, null, true).start();
    final SynchronizedBoolean delay = new SynchronizedBoolean(false);
    final SynchronizedBoolean executed = new SynchronizedBoolean(false);

    ThreadGate delayingGate = new ThreadGate() {
      public void gate(@NotNull final Runnable command) {
        queue.execute(new Runnable() {
          public void run() {
            boolean delayed = delay.commit(true, false);
            if (delayed) {
              sleep(1000);
            }
            command.run();
            executed.set(true);
          }
        });
      }
    };

    TestListener tester = new TestListener(3000);
    view.addListenerFuture(delayingGate, tester);

    // we have`to wait for addListenerFuture() to actually do its work or we can lose the following
    // events
    executed.waitForValue(true);

    // wtf???
    sleep(1000);

    // uncomment this to see debug output
//    allowDebugOutput(Level.INFO);

    delay.set(true);

    // Two transactions must fire consequently without big delay. If you insert "sleep(600)" between
    // next two lines, the test will not fall
    changeArtifact(A, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "2");
    changeArtifact(A, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "3");

    tester.assertValues(null, null, A, null);
  }

  private static final class Singletons extends SingletonCollection {
    private final Singleton<Attribute> attribute;

    public Singletons() {
      attribute = singleton("test", new Initializer() {
        public void initialize(RevisionCreator creator) {
          creator.setValue(ATTRIBUTE.TYPE, TYPE.ATTRIBUTE);
          creator.setValue(ATTRIBUTE.NAME, "test");
          creator.setValue(ATTRIBUTE.VALUETYPE, VALUETYPE.PLAINTEXT);
        }
      });
    }
  }
}
