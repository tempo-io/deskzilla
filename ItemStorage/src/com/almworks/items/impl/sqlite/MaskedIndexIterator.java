package com.almworks.items.impl.sqlite;

import com.almworks.integers.IntIterator;
import com.almworks.integers.LongList;

import java.util.NoSuchElementException;

public class MaskedIndexIterator implements IntIterator {
  private final LongList myBitmaps;
  private final long myMask;
  private final boolean myNegative;

  private int myNext;
  private int mySize;
  private boolean myFound;

  /**
   *
   * @param bitmaps
   * @param mask
   * @param negative if true, bitmap & mask should be equal to 0
   */
  public MaskedIndexIterator(LongList bitmaps, long mask, boolean negative) {
    myBitmaps = bitmaps;
    myMask = mask;
    myNegative = negative;
    myNext = mask == 0 ? -1 : 0;
    mySize = myBitmaps.size();
  }

  public boolean hasNext() {
    findNext();
    return myNext >= 0;
  }

  public int next() {
    findNext();
    if (myNext < 0)
      throw new NoSuchElementException();
    int r = myNext;
    myNext++;
    myFound = false;
    return r;
  }

  private void findNext() {
    if (myFound || myNext < 0)
      return;
    while (myNext < mySize) {
      long bmp = myBitmaps.get(myNext);
      if ((bmp & myMask) != 0L ^ myNegative) {
        myFound = true;
        return;
      }
      myNext++;
    }
    myNext = -1;
  }
}
