package com.almworks.universe;

import org.almworks.util.ArrayUtil;
import org.almworks.util.Log;

import java.util.*;

/**
 * Not a set, actually
 */
public class CompactingSortedSet<T> {
  static final long BULK_ADDITION_THRESHOLD = 2000;
  private static final int SMALL_TREE_SIZE_THRESHOLD = 1000;

  /**
   * Strict comparator results in less zeroes.
   */
  private final Comparator myStrictComparator;

  /**
   * Use loose comparator when comparing with a sample
   */
  private final Comparator myLooseComparator;

  private Object[] myBigArray;
  private TreeSet mySmallTree;

  private boolean myBulkAdding;
  private Object[] myTemporaryAdded;
  private int myTemporaryAddedCount;

  public CompactingSortedSet(Comparator looseComparator, Comparator strictComparator) {
    myLooseComparator = looseComparator;
    myStrictComparator = strictComparator;
  }

  public synchronized boolean isEmpty() {
    return (myBigArray == null || myBigArray.length == 0) && (mySmallTree == null || mySmallTree.size() == 0);
  }

  public synchronized T first() {
    if (myBigArray == null || myBigArray.length == 0) {
      return (mySmallTree == null || mySmallTree.isEmpty()) ? null : (T) mySmallTree.first();
    } else {
      if (mySmallTree == null || mySmallTree.isEmpty()) {
        return (T) myBigArray[0];
      } else {
        T v1 = (T) myBigArray[0];
        T v2 = (T) mySmallTree.first();
        return myStrictComparator.compare(v1, v2) <= 0 ? v1 : v2;
      }
    }
  }

  public synchronized T last() {
    if (myBigArray == null || myBigArray.length == 0) {
      return (mySmallTree == null || mySmallTree.isEmpty()) ? null : (T) mySmallTree.last();
    } else {
      if (mySmallTree == null || mySmallTree.isEmpty()) {
        return (T) myBigArray[myBigArray.length - 1];
      } else {
        T v1 = (T) myBigArray[myBigArray.length - 1];
        T v2 = (T) mySmallTree.last();
        return myStrictComparator.compare(v1, v2) > 0 ? v1 : v2;
      }
    }
  }

  public synchronized T searchExact(Object sample) {
    Object value1 = null;
    Object value2 = null;
    if (mySmallTree != null && !mySmallTree.isEmpty()) {
      SortedSet tailSet = mySmallTree.tailSet(sample);
      if (!tailSet.isEmpty()) {
        Object value = tailSet.first();
        if (myLooseComparator.compare(value, sample) == 0) {
          value1 = value;
        }
      }
    }
    if (myBigArray != null && myBigArray.length > 0) {
      int index = getStartIndex(myBigArray, sample, myLooseComparator);
      if (index < myBigArray.length) {
        Object value = myBigArray[index];
        if (myLooseComparator.compare(value, sample) == 0) {
          value2 = value;
        }
      }
    }
    if (value1 == null) {
      return (T) value2;
    } else if (value2 == null) {
      return (T) value1;
    } else {
      if (myStrictComparator.compare(value1, value2) <= 0) {
        return (T) value1;
      } else {
        return (T) value2;
      }
    }
  }

  private static int getStartIndex(Object[] bigArray, Object sample, Comparator looseComparator) {
    if (bigArray == null) {
      return -1;
    } else {
      if (sample == null) {
        return 0;
      } else {
        int idx = Arrays.binarySearch(bigArray, sample, looseComparator);
        if (idx < 0) {
          idx = -idx - 1;
        }
        // go back until all equal elements are passed
        while (idx > 0) {
          int cmp = looseComparator.compare(bigArray[idx - 1], sample);
          if (cmp == 0) {
            idx--;
          } else if (cmp < 0) {
            break;
          } else {
            assert false : bigArray[idx - 1] + " " + idx + " " + sample;
          }
        }
        return idx;
      }
    }
  }


  /**
   * if sample == null, search all
   */
  public synchronized Iterator<T> iterator(Object sample, Object lock) {
    return new MergeIterator<T>(myBigArray, mySmallTree, sample, lock, myStrictComparator, myLooseComparator);
  }

  public synchronized void add(T value) {
    if (myBulkAdding) {
      assert myTemporaryAdded != null;
      int length = myTemporaryAdded.length;
      assert myTemporaryAddedCount <= length;
      if (myTemporaryAddedCount == length) {
        int newLength = length >= 1000000 ? length + 1000000 : (length + 1) * 2;
        Object[] newArray = new Object[newLength];
        System.arraycopy(myTemporaryAdded, 0, newArray, 0, length);
        myTemporaryAdded = newArray;
      }
      myTemporaryAdded[myTemporaryAddedCount++] = value;
    } else {
      if (mySmallTree == null)
        mySmallTree = new TreeSet<T>(myStrictComparator);
      mySmallTree.add(value);
      checkTreeSize();
    }
  }

  private void checkTreeSize() {
    if (mySmallTree.size() <= SMALL_TREE_SIZE_THRESHOLD)
      return;

    int oldLength = myBigArray == null ? 0 : myBigArray.length;
    int newLength = oldLength + mySmallTree.size();
    Object[] newArray = new Object[newLength];

    // merge
    long start = System.currentTimeMillis();
    int iA = 0;
    Iterator iB = mySmallTree.iterator();
    Object a = iA < oldLength ? myBigArray[iA++] : null;
    Object b = iB.hasNext() ? iB.next() : null;
    for (int k = 0; k < newLength; k++) {
      if (a == null) {
        if (b == null) {
          assert false : k + " " + newLength;
        } else {
          newArray[k] = b;
          b = iB.hasNext() ? iB.next() : null;
        }
      } else {
        if (b == null) {
          newArray[k] = a;
          a = iA < oldLength ? myBigArray[iA++] : null;
        } else {
          int cmp = myStrictComparator.compare(a, b);
          if (cmp <= 0) {
            newArray[k] = a;
            a = iA < oldLength ? myBigArray[iA++] : null;
          } else {
            newArray[k] = b;
            b = iB.hasNext() ? iB.next() : null;
          }
        }
      }
    }
    long timeTaken = System.currentTimeMillis() - start;
    Log.debug("dbu-css: merged " + newLength + " in " + timeTaken + "ms");
    if (a != null || b != null) {
      assert false : a + " " + b;
    }
    assert isArraySorted();

    myBigArray = newArray;
    mySmallTree = null;
  }

  private boolean isArraySorted() {
    if (myBigArray != null && myBigArray.length > 1) {
      for (int i = myBigArray.length - 2; i >= 0; i--) {
        assert
          myStrictComparator.compare(myBigArray[i], myBigArray[i + 1]) <= 0 : myBigArray[i] + " " + myBigArray[i + 1];
      }
    }
    return true;
  }

  public synchronized void startAdding(long estimatedCount) {
    assert !myBulkAdding : this;
    assert myTemporaryAdded == null : this;
    if (estimatedCount > BULK_ADDITION_THRESHOLD) {
      myBulkAdding = true;
      myTemporaryAdded = new Object[Math.min(10000, (int) estimatedCount)];
      myTemporaryAddedCount = 0;
    }
  }

  public synchronized void stopAdding() {
    if (myBulkAdding) {
      assert myTemporaryAdded != null : this;
      if (myTemporaryAddedCount > 0) {
        int oldLength = myBigArray == null ? 0 : myBigArray.length;
        int newLength = oldLength + myTemporaryAddedCount;
        Object[] newArray = new Object[newLength];
        if (oldLength > 0) {
          System.arraycopy(myBigArray, 0, newArray, 0, oldLength);
        }
        System.arraycopy(myTemporaryAdded, 0, newArray, oldLength, myTemporaryAddedCount);
        long startTime = newLength > 2000 ? System.currentTimeMillis() : 0;
        ArrayUtil.quicksort(newArray, myStrictComparator);
        if (startTime != 0) {
          long timeSpent = System.currentTimeMillis() - startTime;
          Log.debug("dbu-css: sorted " + newLength + " in " + timeSpent + "ms");
        }
        myBigArray = newArray;
      }
      myTemporaryAdded = null;
      myTemporaryAddedCount = 0;
      myBulkAdding = false;
    }
  }

  public synchronized void writeStats(StringBuffer result) {
    result.append("cososet arr:")
      .append(myBigArray == null ? 0 : myBigArray.length)
      .append(" tree:")
      .append(mySmallTree == null ? 0 : mySmallTree.size())
      .append(" pend:")
      .append(myTemporaryAddedCount);
  }

  public synchronized boolean contains(T value) {
    return searchExact(value) != null;
  }


  private static class MergeIterator<T> implements Iterator<T> {
    private Object[] myBigArray;
    private Iterator myTreeIterator;
    private final Comparator myComparator;

    private int myArrayIndex;

    private Object myFromArray;
    private Object myFromSet;

    public MergeIterator(Object[] bigArray, TreeSet smallTree, Object sample, Object lock, Comparator strictComparator,
      Comparator looseComparator)
    {
      SortedSet searchSet = (smallTree == null || sample == null) ? smallTree : smallTree.tailSet(sample);
      myTreeIterator = searchSet == null ? null : ConcurrentSortedSetIterator.create(searchSet, lock);
      myBigArray = bigArray;
      myArrayIndex = getStartIndex(bigArray, sample, looseComparator);
      myComparator = strictComparator;
      nextFromArray();
      nextFromSet();
    }

    private Object nextFromSet() {
      Object r = myFromSet;
      if (myTreeIterator == null) {
        myFromSet = null;
      } else if (!myTreeIterator.hasNext()) {
        myFromSet = null;
        // free objects
        myTreeIterator = null;
      } else {
        myFromSet = myTreeIterator.next();
      }
      return r;
    }

    private Object nextFromArray() {
      Object r = myFromArray;
      if (myArrayIndex < 0 || myBigArray == null) {
        myFromArray = null;
      } else if (myArrayIndex >= myBigArray.length) {
        myFromArray = null;
        // free object
        myBigArray = null;
      } else {
        myFromArray = myBigArray[myArrayIndex++];
      }
      return r;
    }

    public boolean hasNext() {
      return myFromArray != null || myFromSet != null;
    }

    public T next() {
      if (myFromArray == null) {
        if (myFromSet == null) {
          throw new NoSuchElementException();
        } else {
          return (T) nextFromSet();
        }
      } else {
        if (myFromSet == null) {
          return (T) nextFromArray();
        } else {
          if (myComparator.compare(myFromArray, myFromSet) <= 0) {
            return (T) nextFromArray();
          } else {
            return (T) nextFromSet();
          }
        }
      }
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
