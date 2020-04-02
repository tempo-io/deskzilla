package com.almworks.universe;

import com.almworks.api.universe.*;
import com.almworks.api.universe.index.FieldComparator;
import com.almworks.api.universe.index.FieldCondition;
import org.almworks.util.RuntimeInterruptedException;
import util.concurrent.CountDown;

import java.util.Iterator;
import java.util.Random;

public class MassiveConcurrencyTest extends UniverseFixture {
  private static final int THREAD_NUMBER = 20;
  private static final int CHAIN_LENGTH = 100;

  private static final int FIELD_COUNTER = -11;
  private static final int FIELD_PREV = -12;
  private static final int FIELD_HEAD = -13;

  private IndexInternal myIndexPrev;
  private IndexInternal myIndexHead;

  public MassiveConcurrencyTest() {
    super(30000);
  }

  protected void setUp() throws Exception {
    super.setUp();
    myIndexPrev = (IndexInternal) myUniverse.createIndex(
      new IndexInfo("prev", FieldComparator.create(FIELD_PREV), FieldCondition.create(FIELD_PREV)));
    myIndexHead = (IndexInternal) myUniverse.createIndex(
      new IndexInfo("head", FieldComparator.create(FIELD_HEAD), FieldCondition.create(FIELD_HEAD)));
  }

  protected void tearDown() throws Exception {
    myIndexPrev = null;
    myIndexHead = null;
    super.tearDown();
  }

  public void testParallelIndexAccess() throws InterruptedException {
    checkParallelIndexAccess();
  }

  public void testParallelIndexAccessIndex2() throws InterruptedException {
    Random random = new Random();
    for (int i = 0; i < CompactingSortedSet.BULK_ADDITION_THRESHOLD * 2; i++) {
      Expansion expansion = myUniverse.begin();
      Atom atom = expansion.createAtom();
      atom.buildJunction(random.nextLong(), Particle.createLong(random.nextLong()));
      Particle me = Particle.createLong(atom.getAtomID());
      atom.buildJunction(FIELD_HEAD, me);
      atom.buildJunction(FIELD_PREV, me);
      expansion.commit();
    }

//    myIndexHead.last();

    checkParallelIndexAccess();
  }

  private void checkParallelIndexAccess() throws InterruptedException {
    // each thread gets to create its own chain, but with the same index condition

    Thread[] threads = new Thread[THREAD_NUMBER];
    CountDown start = new CountDown(threads.length + 1);
    CountDown stop = new CountDown(threads.length);

    for (int i = 0; i < threads.length; i++) {
      threads[i] = new Thread(new Chainer(start, stop), "chainer#" + i);
      threads[i].start();
    }

    start.release();
    start.acquire();
    stop.acquire();
  }

  private class Chainer implements Runnable {
    private final CountDown myStart;
    private final CountDown myStop;

    public Chainer(CountDown start, CountDown stop) {
      myStart = start;
      myStop = stop;
    }

    public void run() {
      try {
        myStart.release();
        myStart.acquire();

        Atom firstAtom = createAtom(FIELD_COUNTER, new Long(0), FIELD_PREV, new Long(-1), FIELD_HEAD, new Long(-1));
        Atom lastAtom = firstAtom;
        for (int i = 1; i < CHAIN_LENGTH; i++) {
          lastAtom = createAtom(FIELD_COUNTER, new Long(i), FIELD_PREV, new Long(lastAtom.getAtomID()), FIELD_HEAD,
            new Long(firstAtom.getAtomID()));
          checkChain(firstAtom, i, lastAtom);
        }
      } catch (InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      } finally {
        myStop.release();
      }
    }

    private void checkChain(Atom firstAtom, int count, Atom lastCommitted) {
      long lastPrev = -1;
      long lastAtom = -1;
      long headAtomID = firstAtom.getAtomID();
      Iterator<Atom> ii = myIndexHead.search(new Long(headAtomID));
      assertEquals(1, myIndexHead.testAtomInSet(lastCommitted));
      int i = 0;
      while (ii.hasNext()) {
        if (count == 0)
          break;

        Atom atom = ii.next();
        if (lastPrev >= 0 && lastAtom >= 0) {
          assertEquals(lastPrev, atom.getAtomID());
          Long sample = new Long(atom.getAtomID());
          Atom last2;
          synchronized (myIndexPrev) {
            last2 = myIndexPrev.searchExact(sample);
            /*
            if (last2 == null) {
              ((IndexImpl) myIndexPrev).dumpIndex();
              System.out.println("\n\ncannot find equiv of [A:" + lastAtom + "], sample: " + sample);
              assertNotNull(last2);
            }
            */
          }
          assertNotNull("cannot find equiv of [A:" + lastAtom + "], sample: " + sample, last2);
          assertEquals(lastAtom, last2.getAtomID());
        }

        long head = atom.getLong(FIELD_HEAD);
        // the condition above (count == 0) has to guarantee that we access at least count of atoms that refer
        // to the first atom.
        assertEquals(headAtomID, head);

        long atomCount = atom.getLong(FIELD_COUNTER);
/*
        if (count != atomCount) {
          synchronized (MassiveConcurrencyTest.class) {
            //            ((IndexImpl) myIndexPrev).dumpIndex();
            System.out.println("\n\nhead " + firstAtom + "; current " + atom + "; thread " + Thread.currentThread()
                .getName());
            System.out.flush();
            System.exit(0);
          }
        }
*/
        assertEquals(":" + i, count, atomCount);

        lastPrev = atom.getLong(FIELD_PREV);
        lastAtom = atom.getAtomID();

        count--;
        i++;
      }
    }
  }
}
