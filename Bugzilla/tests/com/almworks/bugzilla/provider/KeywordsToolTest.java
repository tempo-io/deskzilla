package com.almworks.bugzilla.provider;

import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.tests.CollectionsCompare;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * :todoc:
 *
 * @author sereda
 */
public class KeywordsToolTest extends BaseTestCase {
  public void testKeywordsConversions() {
    CollectionsCompare compare = new CollectionsCompare();
    assertEquals(0, KeywordsTool.splitString(null).size());
    assertEquals(0, KeywordsTool.splitString("").size());
    assertEquals("", KeywordsTool.composeString(new TreeMap<String, String>()));

    SortedMap<String, String> kwds = KeywordsTool.splitString(" D, E, F A B C, C D D       E ");
    compare.order(new String[] {"A", "B", "C", "D", "E", "F"}, kwds.keySet().iterator());
    compare.order(new String[] {"A", "B", "C", "D", "E", "F"}, kwds.values().iterator());

    assertEquals("A B C D E F", KeywordsTool.composeString(kwds));
  }

  public void testKeywordsIngoreCase() {
    CollectionsCompare compare = new CollectionsCompare();
    SortedMap<String, String> kwds = KeywordsTool.splitString("abba BUBBA ABBA buBA");
    compare.order(new String[] {"ABBA", "BUBA", "BUBBA"}, kwds.keySet().iterator());
    compare.order(new String[] {"ABBA", "buBA", "BUBBA"}, kwds.values().iterator());
    assertEquals("ABBA buBA BUBBA", KeywordsTool.composeString(kwds));
  }
}
