package com.almworks.util.collections;

import com.almworks.integers.IntArray;
import com.almworks.integers.LongArray;
import com.almworks.integers.*;
import com.almworks.integers.util.IntSetBuilder;

public class IntLongMapBuilder implements Cloneable {
  private int[] myKeys;
  private long[] myValues;
  private int mySize;
  private boolean myFinished;

  public void mergeFrom(IntSetBuilder set, ValueFunction valueF) {
    mergeFromSortedCollection(set.toSortedCollection(), valueF);
  }

  public void mergeFromSortedCollection(IntList collection, ValueFunction valueF) {
    if (collection.isEmpty())
      return;
    if (myFinished)
      throw new IllegalStateException();
    int otherSize = collection.size();
    int totalLength = mySize + otherSize;
    if (myKeys == null || totalLength > myKeys.length) {
      // merge with reallocation
      merge0withReallocation(collection, otherSize, totalLength, valueF);
    } else {
      merge0inPlace(collection, otherSize, totalLength, valueF);
    }
  }

  private void merge0withReallocation(IntList other, int otherSize, int totalLength, ValueFunction valueF) {
    int newSize = Math.max(totalLength, myKeys == null ? 0 : myKeys.length * 2);
    int[] newKeys = new int[newSize];
    long[] newValues = new long[newSize];
    int pi = 0, pj = 0;
    int i = 0;
    if (mySize > 0 && otherSize > 0) {
      // boundary conditions: quickly merge disjoint sets
      if (myKeys[0] > other.get(otherSize - 1)) {
        other.toArray(0, newKeys, 0, otherSize);
        for (int p = 0; p < otherSize; p++)
          newValues[p] = valueF.value(p, other.get(p), false, 0L);
        i = pj = otherSize;
      } else if (myKeys[mySize - 1] < other.get(0)) {
        System.arraycopy(myKeys, 0, newKeys, 0, mySize);
        System.arraycopy(myValues, 0, newValues, 0, mySize);
        i = pi = mySize;
      }
    }
    while (pi < mySize && pj < otherSize) {
      int vi = myKeys[pi];
      int vj = other.get(pj);
      if (vi < vj) {
        newKeys[i] = vi;
        newValues[i] = myValues[pi];
        i++;
        pi++;
      } else if (vi > vj) {
        newKeys[i] = vj;
        newValues[i] = valueF.value(pj, vj, false, 0L);
        i++;
        pj++;
      } else {
        assert vi == vj;
        newKeys[i] = vi;
        newValues[i] = valueF.value(pj, vj, true, myValues[pi]);
        i++;
        pi++;
        pj++;
      }
    }
    if (pi < mySize) {
      int size = mySize - pi;
      System.arraycopy(myKeys, pi, newKeys, i, size);
      System.arraycopy(myValues, pi, newValues, i, size);
      i += size;
    } else if (pj < otherSize) {
      int size = otherSize - pj;
      other.toArray(pj, newKeys, i, size);
      for (int p = 0; p < size; p++)
        newValues[i + p] = valueF.value(pj + p, other.get(pj + p), false, 0L);
      i += size;
    }
    myKeys = newKeys;
    myValues = newValues;
    mySize = i;
  }

  private void merge0inPlace(IntList other, int otherSize, int totalLength, ValueFunction valueF) {
    // in place
    // 1. find offset (scan for duplicates)
    // 2. merge starting from the end
    if (mySize > 0 && otherSize > 0 && myKeys[0] > other.get(otherSize - 1)) {
      System.arraycopy(myKeys, 0, myKeys, otherSize, mySize);
      System.arraycopy(myValues, 0, myValues, otherSize, mySize);
      other.toArray(0, myKeys, 0, otherSize);
      for (int p = 0; p < otherSize; p++)
        myValues[p] = valueF.value(p, other.get(p), false, 0L);
      mySize = totalLength;
    } else if (mySize > 0 && otherSize > 0 && myKeys[mySize - 1] < other.get(0)) {
      other.toArray(0, myKeys, mySize, otherSize);
      for (int p = 0; p < otherSize; p++)
        myValues[mySize + p] = valueF.value(p, other.get(p), false, 0L);
      mySize = totalLength;
    } else {
      int insertCount = 0;
      int pi = 0, pj = 0;
      while (pi < mySize && pj < otherSize) {
        int vi = myKeys[pi];
        int vj = other.get(pj);
        if (vi < vj) {
          pi++;
        } else if (vi > vj) {
          pj++;
          insertCount++;
        } else {
          assert vi == vj;
          pi++;
          pj++;
        }
      }
      insertCount += (otherSize - pj);
      pi = mySize - 1;
      pj = otherSize - 1;
      int i = mySize + insertCount;
      while (pi >= 0 && pj >= 0) {
        assert i > pi : i + " " + pi;
        int vi = myKeys[pi];
        int vj = other.get(pj);
        if (vi < vj) {
          --i;
          myKeys[i] = vj;
          myValues[i] = valueF.value(pj, vj, false, 0L);
          pj--;
        } else if (vi > vj) {
          --i;
          myKeys[i] = vi;
          myValues[i] = myValues[pi];
          pi--;
        } else {
          assert vi == vj;
          --i;
          myKeys[i] = vi;
          myValues[i] = valueF.value(pj, vj, true, myValues[pi]);
          pi--;
          pj--;
        }
      }
      if (pj >= 0) {
        int size = pj + 1;
        other.toArray(0, myKeys, 0, size);
        for (int p = 0; p < size; p++)
          myValues[p] = valueF.value(p, other.get(p), false, 0L);
        i -= size;
      } else if (pi >= 0) {
        i -= pi + 1;
      }
      assert i == 0 : i;
      mySize += insertCount;
    }
  }

  public IntList keysSortedCollection() {
    myFinished = true;
    if (mySize == 0)
      return IntList.EMPTY;
    return new IntArray(myKeys, mySize);
  }

  public LongList valuesCollection() {
    myFinished = true;
    if (mySize == 0)
      return LongList.EMPTY;
    return new LongArray(myValues, mySize);
  }

  protected IntLongMapBuilder clone() {
    IntLongMapBuilder r = null;
    try {
      r = (IntLongMapBuilder) super.clone();
    } catch (Exception e) {
      throw new Error(e);
    }
    r.myFinished = false;
    if (r.myKeys != null)
      r.myKeys = r.myKeys.clone();
    if (r.myValues != null)
      r.myValues = r.myValues.clone();
    return r;
  }

  public boolean isEmpty() {
    return mySize == 0;
  }

  public void clear(boolean reuseArrays) {
    mySize = 0;
    if (myFinished && !reuseArrays) {
      myKeys = null;
      myValues = null;
    }
    myFinished = false;
  }


  public static interface ValueFunction {
    long value(int index, int key, boolean hasEntry, long oldValue);
  }
}
