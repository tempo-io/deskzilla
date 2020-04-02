package com.almworks.universe;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ConcurrentSortedSetIterator <T> implements Iterator<T> {
  private static final int MAX_ATTEMPTS = 20;

  private final Object myLock;
  private final SortedSet<T> myOriginalSet;

  private Iterator<T> myCurrentIterator = null;
  private T myLastResult = null;

  public ConcurrentSortedSetIterator(SortedSet<T> originalSet, Object lock) {
    assert lock != null;
    myOriginalSet = originalSet;
    myLock = lock;
//    myLock = new Object(); - for debugging errors
    recreateIterator();
  }

  public static <T> Iterator<T> create(SortedSet<T> set, Object lock) {
    return new ConcurrentSortedSetIterator<T>(set, lock);
  }

  public boolean hasNext() {
    synchronized (myLock) {
      if (myCurrentIterator == null)
        return false;
      if (myCurrentIterator.hasNext())
        return true;
      myCurrentIterator = null;
    }
    return false;
  }

  public T next() {
    synchronized (myLock) {
      if (myCurrentIterator == null)
        throw new NoSuchElementException();
      for (int i = 0; i < MAX_ATTEMPTS; i++) {
        try {
          myLastResult = myCurrentIterator.next();
          return myLastResult;
        } catch (ConcurrentModificationException e) {
//          System.out.println("cme " + Thread.currentThread().getName());
          recreateIterator();
        }
      }
    }
    throw new ConcurrentModificationException();
  }

  public void remove() {
    synchronized (myLock) {
      if (myCurrentIterator == null)
        throw new NoSuchElementException();
      for (int i = 0; i < MAX_ATTEMPTS; i++) {
        try {
          myCurrentIterator.remove();
          return;
        } catch (ConcurrentModificationException e) {
          recreateIterator();
        }
      }
      throw new ConcurrentModificationException();
    }
  }

  private void recreateIterator() {
    synchronized (myLock) {
      if (myLastResult == null) {
        myCurrentIterator = myOriginalSet.iterator();
      } else {
        try {
          myCurrentIterator = myOriginalSet.tailSet(myLastResult).iterator();
        } catch (IllegalArgumentException ee) {
          // last result is now somewhere else
          throw new ConcurrentModificationException();
        }
        if (!myCurrentIterator.hasNext())
          throw new ConcurrentModificationException();
        myCurrentIterator.next();
      }
    }
  }
}
