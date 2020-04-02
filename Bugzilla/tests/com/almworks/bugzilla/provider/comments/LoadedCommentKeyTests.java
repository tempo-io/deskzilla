package com.almworks.bugzilla.provider.comments;

import com.almworks.util.tests.BaseTestCase;

/**
 * @author dyoma
 */
public class LoadedCommentKeyTests extends BaseTestCase {
  public void testOrder() {
    checkEarlier(key(0), key(1));
    checkEarlier(key(1), key(2));
    checkEarlier(key(10), key(-1));
    checkEarlier(key(-2), key(-3));
    checkEqual(key(0), key(0));
    checkEqual(key(20), key(20));
    checkEqual(key(-1), key(-1));
  }

  private LoadedCommentKey.CommentPlace key(int intKey) {
    return LoadedCommentKey.CommentPlace.create(intKey);
  }

  private void checkEarlier(LoadedCommentKey.CommentPlace earlier, LoadedCommentKey.CommentPlace later) {
    assertTrue(earlier + " < " + later, earlier.isBefore(later));
    assertTrue(later + " > " + earlier, later.isAfter(earlier));
    assertFalse(earlier + " != " + later,  earlier.equals(later));
  }

  private void checkEqual(LoadedCommentKey.CommentPlace k1, LoadedCommentKey.CommentPlace k2) {
    assertEquals(k1, k2);
    assertEquals(k1.hashCode(), k2.hashCode());
    assertFalse(k1.isBefore(k2));
    assertFalse(k1.isAfter(k2));
    assertFalse(k2.isBefore(k1));
    assertFalse(k2.isAfter(k1));
  }
}
