package com.almworks.items.impl;

import com.almworks.integers.LongIterable;
import com.almworks.integers.LongIterator;
import com.almworks.integers.util.SortedLongListIntersectionIterator;
import com.almworks.integers.util.SortedLongListMinusIterator;

public class CombiningIterable implements LongIterable {
  private final LongIterable myInputItems;
  private final LongIterable myReferredItems;
  private final boolean myExclude;

  public CombiningIterable(LongIterable inputItems, LongIterable referredItems, boolean exclude) {
    myInputItems = inputItems;
    myReferredItems = referredItems;
    myExclude = exclude;
  }

  public LongIterator iterator() {
    return myExclude ? new SortedLongListMinusIterator(myInputItems.iterator(), myReferredItems.iterator()) :
      new SortedLongListIntersectionIterator(myInputItems.iterator(), myReferredItems.iterator());
  }
}
