package com.almworks.universe;

import com.almworks.api.universe.*;
import com.almworks.misc.TestWorkArea;
import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.RuntimeInterruptedException;
import util.concurrent.CountDown;

import java.util.Iterator;

public class FileUniverseTests extends BaseTestCase {
  private FileUniverse myUniverse;
  private TestWorkArea myWorkArea;

  protected void setUp() throws Exception {
    myWorkArea = new TestWorkArea();
    myUniverse = new FileUniverse(myWorkArea.getDatabaseDir());
    myUniverse.start();
  }

  protected void tearDown() throws Exception {
    myUniverse.stop();
    myUniverse = null;
    myWorkArea.cleanUp();
  }

  public void testPersitence() throws Exception {
    long field = 100;
    Expansion expansion = myUniverse.begin();
    Atom atom = expansion.createAtom();
    long atomID = atom.getAtomID();
    atom.buildJunction(field, Particle.create("f2"));
    expansion.commit();
    long ucn = expansion.getCommitUCN();
    myUniverse.stop();

    myUniverse = new FileUniverse(myWorkArea.getDatabaseDir());
    myUniverse.start();
    assertTrue(myUniverse.getUCN() > ucn);

    Iterator<Atom> iterator = myUniverse.getGlobalIndex().all();
    atom = iterator.next();
    assertEquals(atomID, atom.getAtomID());
    assertEquals(ucn, atom.getUCN());
    assertEquals("f2", atom.getString(field));

    assertFalse(iterator.hasNext());
  }


  public void testConcurrentReadWrite() throws Exception {
    // 1. create data
    createAtoms(1000, 80);
    myUniverse.stop();

    myUniverse = new FileUniverse(myWorkArea.getDatabaseDir());
    myUniverse.start();
    Index index = myUniverse.getGlobalIndex();
    final Iterator<Atom> ii = index.all();

    final CountDown start = new CountDown(3);
    final CountDown stop = new CountDown(3);

    // 2. run in parallel: data creation and hosted reading
    new Thread() {
      public void run() {
        try {
          start.release();
          start.acquire();
          int sum = 0;
          while(ii.hasNext()) {
            Atom a = ii.next();
            Particle p = a.get(99);
            if (p == null)
              continue;
            assertTrue(p instanceof Particle.PFileHostedBytes);
            byte[] bytes = p.raw();
            sum += bytes.length;
          }
        } catch (InterruptedException e) {
          throw new RuntimeInterruptedException(e);
        } finally {
          stop.release();
        }
      }
    }.start();

    new Thread() {
      public void run() {
        try {
          start.release();
          start.acquire();
          createAtoms(40000, 1);
        } catch (InterruptedException e) {
          throw new RuntimeInterruptedException(e);
        } finally {
          stop.release();
        }
      }
    }.start();

    // go!
    start.release();
    stop.release();
    stop.acquire();
    long ucn = myUniverse.getUCN();

    myUniverse.stop();
    myUniverse = new FileUniverse(myWorkArea.getDatabaseDir());
    myUniverse.start();

    assertEquals(ucn, myUniverse.getUCN());
  }

  private void createAtoms(int expansions, int atomsPerExpansion) {
    for (int i = 0; i < expansions; i++) {
      Expansion expansion = myUniverse.begin();
      for (int j = 0; j < atomsPerExpansion; j++) {
        byte[] data = createTestData(100, i);
        // first 4 bytes and higher bit of byte 5 have to be zeroes to fit into int (constraint for hosted particles)
        data[0] = data[1] = data[2] = data[3] = 0;
        data[4] = 1;
        expansion.createAtom().buildJunction(99, Particle.createBytes(data));
      }
      expansion.commit();
    }
  }
}
