package com.almworks.universe;

import com.almworks.util.collections.Containers;
import com.almworks.util.tests.BaseTestCase;

import java.util.*;

public class CompactingSortingSetTests extends BaseTestCase {
  public static final Comparator LOOSE_COMPARATOR = new Comparator() {
    public int compare(Object o1, Object o2) {
      return Containers.compareInts(get(o1), get(o2));
    }

    private int get(Object o) {
      if (o instanceof Datum)
        return ((Datum) o).myOrder;
      else if (o instanceof Integer)
        return (Integer) o;
      else
        throw new RuntimeException(String.valueOf(o));
    }
  };

  public static final Comparator STRICT_COMPARATOR = new Comparator() {
    public int compare(Object o1, Object o2) {
      int r = LOOSE_COMPARATOR.compare(o1, o2);
      if (r != 0)
        return r;
      return String.CASE_INSENSITIVE_ORDER.compare(get(o1), get(o2));
    }

    private String get(Object o) {
      if (o instanceof Datum)
        return ((Datum) o).myString;
      else
        return "";
    }
  };

  private CompactingSortedSet<Datum> mySet;
  private final Random myRandom = new Random();

  protected void setUp() throws Exception {
    super.setUp();
    reset();
  }

  private void reset() {
    mySet = new CompactingSortedSet<Datum>(LOOSE_COMPARATOR, STRICT_COMPARATOR);
  }

  protected void tearDown() throws Exception {
    mySet = null;
    super.tearDown();
  }

  public void testEmpty() {
    assertNull(mySet.first());
    assertNull(mySet.last());
    assertTrue(mySet.isEmpty());
    Iterator<Datum> ii = mySet.iterator(null, this);
    assertFalse(ii.hasNext());
    try {
      ii.next();
      fail();
    } catch (NoSuchElementException e) {
      // normal
    }
    ii = mySet.iterator(1, this);
    assertFalse(ii.hasNext());
    try {
      ii.next();
      fail();
    } catch (NoSuchElementException e) {
      // normal
    }
  }

  public void testGettingFirstOfEqual() {
    for (int i = 0; i < 3; i++) {
      reset();
      doTestGettingFirstOfEqual();
    }
  }

  private void doTestGettingFirstOfEqual() {
    int BIG = 10000;
    int SMALL = 100;
    int SOUGHT = 1000;
    addMany(BIG, 100, 999);
    addFew(SMALL, 1001, 1999);

    // add sought value
    for (int i = 0; i < 5; i++)
      add(SOUGHT);
    addMany(5, SOUGHT, SOUGHT + 1);

    addMany(BIG, 1001, 1999);
    addFew(SMALL, 100, 999);

    checkCount(10, SOUGHT);
  }

  private void checkCount(int expected, int value) {
    Iterator<Datum> ii = mySet.iterator(value, this);
    int count = 0;
    while (ii.hasNext()) {
      Datum datum = ii.next();
      if (datum.myOrder == value)
        count++;
      else
        break;
    }

    assertEquals(expected, count);
  }

  public void testMergingSmallTree() {
    for (int i = 0; i < 10; i++) {
      addFew(1000, 1001, 1999);
      add(55);
      addFew(1000, 100, 999);
    }
    checkCount(10, 55);
  }

  public void testMergingSmallTree2() {
    for (int i = 0; i < 10; i++) {
      addMany(10000, 10001, 19999);
      addFew(1000, 1001, 1999);
      add(55);
      addFew(1000, 100, 999);
    }
    checkCount(10, 55);
  }

  private void addFew(int count, int from, int to) {
    doAdd(count, from, to);
  }

  private void doAdd(int count, int from, int to) {
    int size = to - from;
    for (int i = 0; i < count; i++) {
      int v = from + myRandom.nextInt(size);
      add(v);
    }
  }

  private void add(int v) {
    mySet.add(new Datum(v, String.valueOf(myRandom.nextFloat())));
  }

  private void addMany(int count, int from, int to) {
    mySet.startAdding(count * 3 / 2);
    doAdd(count, from, to);
    mySet.stopAdding();
  }

  private static class Datum {
    private final int myOrder;
    private final String myString;

    public Datum(int order, String string) {
      myOrder = order;
      myString = string;
    }

    public String toString() {
      return myOrder + ":" + myString;
    }
  }
}
