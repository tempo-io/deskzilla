package com.almworks.universe;

import com.almworks.api.universe.*;
import com.almworks.api.universe.index.FieldComparator;
import com.almworks.api.universe.index.FieldCondition;
import com.almworks.util.collections.CollectionUtil;

import java.util.Iterator;

/**
 * :todoc:
 *
 * @author sereda
 */
public class MemUniverseTests extends UniverseFixture {
  public void testEmpty() {
    assertTrue(myUniverse.getGlobalIndex().first() == null);

    assertTrue(myUniverse.getIndices(null).length == 1);

    assertTrue(myUniverse.isDefaultIndexing());
  }

  public void testCreateOneAtom() {
    long f1 = 101;
    long f2 = 102;
    Expansion expansion = myUniverse.begin();
    long atomID = createAtom(f1, "xxx").getAtomID();
    assertTrue(atomID >= 0);
    Expansion.Result result = expansion.commit();
    assertTrue(result.isSuccessful());
    assertTrue(result.getException() == null);
    assertTrue(result.getVerificationException() == null);
    Atom atom = myUniverse.getAtom(atomID);
    assertTrue(atom.getAtomID() == atomID);
    assertTrue("xxx".equals(atom.getString(f1)));
    assertTrue(atom.getString(f2) == null);
  }

  public void testUCNIncrease() {
    long last_ucn = myUniverse.getUCN();
    for (int i = 0; i < 10; i++) {
      Expansion expansion = myUniverse.begin();
      expansion.createAtom();
      expansion.commit();
      long new_ucn = myUniverse.getUCN();
      assertTrue(new_ucn > last_ucn);
      last_ucn = new_ucn;
    }
    for (int i = 0; i < 10; i++) {
      Expansion expansion = myUniverse.begin();
      createAtom(1, "y");
      expansion.commit();
      long new_ucn = myUniverse.getUCN();
      assertTrue(new_ucn > last_ucn);
      last_ucn = new_ucn;
    }
  }

  public void testGetAtom() {
    Expansion expansion = myUniverse.begin();
    long atomID = createAtom(expansion, 1, "y").getAtomID();
    assertTrue(myUniverse.getAtom(atomID) == null);
    expansion.commit();
    Atom atom = myUniverse.getAtom(atomID);
    assertTrue(atom != null);
    assertTrue("y".equals(atom.getString(1)));
  }

  public void testTransaction() {
    Expansion expansion = myUniverse.begin();
    long startUCN = expansion.getStartUCN();
    assertTrue(startUCN == myUniverse.getUCN());
    final boolean[] verified = {false};

    expansion.createAtom();

    expansion.addVerifier(new Verifier() {
      public void verify() throws ExpansionVerificationException {
        verified[0] = true;
      }
    });

    assertTrue(!verified[0]);
    expansion.commit();
    assertTrue(verified[0]);

    assertTrue(expansion.getStartUCN() == startUCN);
    assertTrue(expansion.getCommitUCN() == startUCN);
  }

  public void testVerification() {
    Expansion expansion = myUniverse.begin();
    long atomID1 = createAtom(expansion, 1, "y").getAtomID();
    expansion.addVerifier(new Verifier() {
      public void verify() throws ExpansionVerificationException {
        throw new ExpansionVerificationException();
      }
    });
    long atomID2 = createAtom(expansion, 2, "y").getAtomID();
    Expansion.Result result = expansion.commit();
    assertTrue(!result.isSuccessful());
    assertTrue(result.getVerificationException() != null);

    assertEquals(null, myUniverse.getAtom(atomID1));
    assertEquals(null, myUniverse.getAtom(atomID2));
  }

  public void testRollback() {
    Expansion expansion = myUniverse.begin();
    long atomID = createAtom(expansion, 1, "y").getAtomID();
    expansion.rollback();
    assertTrue(myUniverse.getAtom(atomID) == null);
  }

  public void testGlobalIndex() {
    long[] atoms = new long[3];
    Expansion[] transactions = new Expansion[atoms.length];
    for (int i = 0; i < transactions.length; i++)
      transactions[i] = myUniverse.begin();
    for (int i = 0; i < transactions.length; i++) {
      Atom atom = transactions[i].createAtom();
      atom.buildJunction(100 + i, Particle.create("x" + i));
      atoms[i] = atom.getAtomID();
      transactions[i].commit();
    }

    for (int i = 0; i < atoms.length; i++) {
      Long ucn = new Long(transactions[i].getCommitUCN());
      Atom atom = myUniverse.getGlobalIndex().searchExact(ucn);
      assertTrue(atom.getAtomID() == atoms[i]);
    }

    Iterator<Atom> iterator = myUniverse.getIndices(null)[0].all();
    int i = atoms.length;
    while (iterator.hasNext()) {
      Atom atom = iterator.next();
      assertEquals(atoms[--i], atom.getAtomID());
    }
    assertEquals(0, i);
  }

  public void testFieldIndex() {
    final long field = 100;
    int oldIndexCount = myUniverse.getIndices().length;
    IndexInfo indexInfo = new IndexInfo("i", FieldComparator.create(field), FieldCondition.create(field));
    Index index = myUniverse.createIndex(indexInfo);
    assertTrue(myUniverse.getIndex("i") != null);
    assertTrue(myUniverse.getIndices().length == oldIndexCount + 1);

    Expansion expansion = myUniverse.begin();
    createAtom(field, "vkjsnvfkfjnvkfjnvskfjnv");
    createAtom(field, "cd");
    createAtom(field + 1, "cd");
    createAtom(field, null, field + 1, "dsd", field + 2, "dfv");
    createAtom(field, null);
    createAtom(field + 4, null);
    createAtom(field + 3, null, field + 1, "dsd", field + 2, "sdfv");
    createAtom(field, new byte[]{3, 5, 6, 1, 4, 6});
    expansion.commit();

    Iterator<Atom> iterator = index.all();
    int count = 0;
    for (; iterator.hasNext(); iterator.next())
      count++;
    assertEquals(5, count);

    byte[] last_array = {};
    for (iterator = index.all(); iterator.hasNext();) {
      Atom atom = iterator.next();
      byte[] next_array = atom.get(field).raw();
      assertTrue(CollectionUtil.compareArrays(next_array, last_array) >= 0);
      last_array = next_array;
    }

    Atom atom = index.searchExact("cd");
    assertTrue(atom != null);
    assertTrue("cd".equals(atom.getString(field)));
  }

  public void testAccessEtherealAtom() {
    Expansion expansion = myUniverse.begin();
    Atom atom = expansion.createAtom();
    myUniverse.getGlobalIndex().last();
  }

  public void testUCN() {
    Expansion expansion = myUniverse.begin();
    createAtom(expansion, 100, "xxx");
    createAtom(expansion, 200, "v");
    expansion.commit();
    expansion = myUniverse.begin();
    createAtom(expansion, 300, "z");
    expansion.commit();

    long ucn = myUniverse.getUCN();
    for (Iterator<Atom> it = myUniverse.getGlobalIndex().all(); it.hasNext();)
      assertTrue(it.next().getUCN() < ucn);

    expansion = myUniverse.begin();
    assertTrue(expansion.getStartUCN() == ucn);

    long id = createAtom(expansion, 400, "fdv").getAtomID();
    expansion.commit();
    Atom atom = myUniverse.getAtom(id);

    assertEquals(ucn, atom.getUCN());
    assertTrue(atom.getUCN() == expansion.getCommitUCN());
    assertTrue(myUniverse.getUCN() == ucn + 1);
  }

  public void testGlobalIndexOrder() {
    final long field = 100;
    createAtom(field, "1");
    createAtom(field, "2");
    createAtom(field, "3");
    // we should access them in back order
    Iterator<Atom> all = myUniverse.getGlobalIndex().all();
    assertEquals("3", all.next().getString(field));
    assertEquals("2", all.next().getString(field));
    assertEquals("1", all.next().getString(field));
    assertFalse(all.hasNext());
  }
}
