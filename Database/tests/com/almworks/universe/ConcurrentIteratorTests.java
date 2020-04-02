package com.almworks.universe;

import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.Collections15;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ConcurrentIteratorTests extends BaseTestCase {
  private SortedSet<String> set;
  private static final String[] values = {
    "cs", "1", "1x", "3", "svgbw as - fvdsa fvdsa fvd", "sss", "1d", "51", "52", "53"};

  static {
    Arrays.sort(values);
  }

  protected void setUp() throws Exception {

    set = Collections15.treeSet();
    set.addAll(Arrays.asList(values));
  }

  private void checkIterator(String prefix, Iterator<String> iterator, int position) {
    for (; position < values.length; position++) {
      assertTrue(prefix, iterator.hasNext());
      assertEquals(prefix, values[position], iterator.next());
    }
    assertFalse(prefix, iterator.hasNext());
  }

  private void flip() {
    set.remove("1");
    set.add("1");
  }

  public void testIterator() {
    for (int i = 0; i < values.length; i++) {
      Iterator<String> iterator = createIterator();
      for (int j = 0; j < i; j++)
        iterator.next();
      checkIterator("p1:" + i, iterator, i);
    }

    for (int i = 0; i < values.length; i++) {
      Iterator<String> iterator = createIterator();
      for (int j = 0; j < i; j++) {
        flip();
        iterator.next();
      }
      checkIterator("p2:" + i, iterator, i);
    }

    for (int i = 0; i < set.size(); i++) {
      Iterator<String> iterator = createIterator();
      for (int j = 0; j < i; j++) {
        flip();
        iterator.next();
        iterator.remove();
      }
      while (iterator.hasNext())
        iterator.next();
    }
  }

  public void testAdditionAfter() {
    set.remove("sss");
    Iterator<String> iterator = createIterator();
    iterator.next();
    iterator.next();
    set.add("sss");
    checkIterator("", iterator, 2);
  }

  public void testAdditionBefore() {
    set.remove("1");
    Iterator<String> iterator = createIterator();
    iterator.next();
    iterator.next();
    iterator.next();
    set.add("1");
    checkIterator("", iterator, 4);
  }

  private Iterator<String> createIterator() {
    return ConcurrentSortedSetIterator.create(set, new Object());
  }
}
