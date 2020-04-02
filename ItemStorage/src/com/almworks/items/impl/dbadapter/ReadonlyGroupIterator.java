package com.almworks.items.impl.dbadapter;

import com.almworks.integers.IntArray;
import com.almworks.integers.WritableIntList;

import java.util.NoSuchElementException;

/**
 * Iterates over a distribution of items between groups in a multiple grouping without changing the distribution or groupings. 
 * @author igor baltiyskiy
 */
class ReadonlyGroupIterator implements AbstractGroupsDist.Iterator {
  /**
   * The iterated group distribution.
   */
  private AbstractGroupsDist myGroups;
  /**
   * Each element corresponds to a grouping and contains index of the first element of the next group in the grouping.
   */
  private WritableIntList myNextGroups = new IntArray();
  /**
   * The value that will be returned after the subsequent call of {@link #next()}.
   */
  private int myNext;
  /**
   * If true, the subsequent call to {@link #next()} will succeed without throwing {@link java.util.NoSuchElementException}.
   */
  private boolean myHasNext;
  /**
   * Group where the event takes place: myNextGroups[myEventGrouping] is the number of the leftmost grouping such that the next group comes sooner than in other groups.
   */
  private int myEventGrouping;
  /**
   * The previous value of {@link #myEventGrouping}.
   */
  private int myLastEventGrouping;

  public ReadonlyGroupIterator(int start, AbstractGroupsDist groups) {
    myGroups = groups;
    myNextGroups.insertMultiple(0, start, groups.groupingsCount());
    calcNext();
  }

  public boolean hasNext() throws NoSuchElementException {
    return myHasNext;
  }

  public int startedGroupIndex() {
    return myLastEventGrouping;
  }

  public int next() throws NoSuchElementException {
    if(!hasNext()) throw new NoSuchElementException();
    int ret = myNext;
    myLastEventGrouping = myEventGrouping;
    calcNext();
    return ret;
  }

  /**
   * Sweeping line-like approach: we maintain the list of the nearest next group IDs.
   * Event: a new group has started in some grouping column => started in all columns to the right.
   */
  private void calcNext() {
    myEventGrouping = -1;
    for(int i = 0; i < myGroups.groupingsCount(); ++i) {
      if(myNextGroups.get(i) >= 0 && (myEventGrouping < 0 || myNextGroups.get(i) < myNextGroups.get(myEventGrouping))) {
        myEventGrouping = i;
      }
    }
    if(myEventGrouping < 0) {
      myHasNext = false;
      return;
    }
    myHasNext = true;
    myNext = myNextGroups.get(myEventGrouping);
    for(int i = myEventGrouping; i < myGroups.groupingsCount(); ++i) {
      // sweep
      int g = myNextGroups.get(i);
      if (g == myNext) {
        myNextGroups.set(i, myGroups.getGrouping(i).getNextDifferentValueIndex(g));
      }
    }
  }

  /**
   * Returns [&lt;{@link #startedGroupIndex()}&gt;, &lt;value that will be returned by the next call to {@link #next()}&gt;].
   * @return
   */
  @Override
  public String toString() {
    return "next group: [" + startedGroupIndex() + ", " + myNext + ']';
  }
}
