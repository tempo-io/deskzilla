package com.almworks.universe;

import com.almworks.api.universe.*;
import com.almworks.api.universe.index.FieldComparator;
import com.almworks.util.commons.Condition;
import util.concurrent.CountDown;

public class ConcurrencyRegressionTests extends UniverseFixture {
  private static final int LAG = 400;
  private Index myIndex;

  protected void setUp() throws Exception {
    myUniverse = new TestUniverse();
    myIndex = myUniverse.createIndex(new IndexInfo("global", FieldComparator.create(1), Condition.<Atom>always()));
  }

  protected void tearDown() throws Exception {
    myUniverse = null;
    myIndex = null;
  }

  public void testIndexIsBrokenBecauseUCNGetsUpdatedAheadOfAtoms() throws InterruptedException {
    // Problem: When doing Expansion.commit(), first, UCN is updated. Then all atoms are moved into their
    // places. If there's an index that is working in parallel with commit, it may see the new UCN before
    // real atoms are there. It will update its cache and because it doesn't see atoms, it will continue
    // to recognize them as ethereal.
    // Solved: moved ucn promotion and atom filling under state lock. (see State)

    // Test works as follows:
    // Main thread: prepare expansion - SYNC - commit (with LAG between UCN promotion and atom filling) - check
    // Additional thread: SYNC - wait LAG/2 (ensure we're in the middle of commit) - update (corrupt) index

    final CountDown start = new CountDown(2);
    final CountDown stop = new CountDown(1);

    new Thread() {
      public void run() {
        try {
          start.release();
          start.acquire();

          // Make sure we're deep in commit
          sleep(LAG / 2);

          // This would cause IndexImpl.update() to go through the newest state of atoms
          myIndex.last();

        } catch (InterruptedException e) {
        } finally {
          stop.release();
        }
      }
    }.start();

    // prepare victim
    Expansion expansion = myUniverse.begin();
    Atom atom = expansion.createAtom();
    atom.buildJunction(1, Particle.createLong(2));
    long atomID = atom.getAtomID();

    // test goes here
    start.release();
    start.acquire();

    expansion.commit();
    stop.acquire();

    // now index is corrupt - we would not see committed atom because it is still considered Ethereal.
    Atom lastAtom = myIndex.last();
    assertNotNull(lastAtom);
    assertEquals(atomID, lastAtom.getAtomID());
  }



  /**
   * This test universe is needed to ensure LAG, so we catch race condition
   */
  public static class TestUniverse extends MemUniverse {
    public Expansion begin() {
      return new TestExpansion(myState);
    }
  }

  public static class TestExpansion extends MemExpansionImpl {
    public TestExpansion(State state) {
      super(state);
    }

    public synchronized Atom createAtom() {
      final long slot = myState.allocateSlot();
      Atom atom = new TestAtom(slot, 0);
      myAtoms.put(new Long(slot), atom);
      return atom;
    }
  }

  public static class TestAtom extends Atom {
    public TestAtom(long atomID, int initialJunctionCount) {
      super(atomID, initialJunctionCount);
    }

    public synchronized void buildFinished(long ucn) {
      super.buildFinished(ucn);
      // allow test to fail
      sleep(LAG);
    }
  }
}
