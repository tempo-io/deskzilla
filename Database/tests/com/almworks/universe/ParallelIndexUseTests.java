package com.almworks.universe;

import com.almworks.api.universe.*;
import com.almworks.api.universe.index.FieldComparator;
import com.almworks.api.universe.index.FieldCondition;

import java.util.Iterator;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ParallelIndexUseTests extends UniverseFixture {
  public void testConcurrentModification() {
    final long field = 100;
    IndexInfo indexInfo = new IndexInfo("i", FieldComparator.create(field), FieldCondition.create(field));
    Index index = myUniverse.createIndex(indexInfo);

    createAtom(field, "1");
    createAtom(field, "2");
    createAtom(field, "2");
    createAtom(field, "3");

    Iterator<Atom> all = myUniverse.getGlobalIndex().all();
    Iterator<Atom> search2 = index.search("2");

    assertEquals("3", all.next().getString(field));
    assertEquals("2", search2.next().getString(field));

    createAtom(field, "4");
    createAtom(field, "5");
    createAtom(field, "2");

    myUniverse.getGlobalIndex().all();
    myUniverse.getIndex(indexInfo.getName()).all();

    assertEquals("2", all.next().getString(field));
    assertEquals("2", all.next().getString(field));
    assertEquals("1", all.next().getString(field));
    assertFalse(all.hasNext());

    assertEquals("2", search2.next().getString(field));
  }
}

