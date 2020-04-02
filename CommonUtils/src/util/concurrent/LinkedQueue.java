/*
  File: LinkedQueue.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

  History:
  Date       Who                What
  11Jun1998  dl               Create public version
  25aug1998  dl               added peek
  10dec1998  dl               added isEmpty
  10oct1999  dl               lock on node object to ensure visibility
  07jan2005  is               added Genericization
*/

package util.concurrent;

/**
 * A linked list based channel implementation.
 * The algorithm avoids contention between puts
 * and takes when the queue is not empty.
 * Normally a put and a take can proceed simultaneously.
 * (Although it does not allow multiple concurrent puts or takes.)
 * This class tends to perform more efficently than
 * other Channel implementations in producer/consumer
 * applications.
 * <p>[<a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html"> Introduction to this package. </a>]
 */

public class LinkedQueue <T> implements Channel<T> {


  /**
   * Dummy header node of list. The first actual node, if it exists, is always
   * at head_.next. After each take, the old first node becomes the head.
   */
  protected LinkedNode<T> head_;

  /**
   * Helper monitor for managing access to last node.
   */
  protected final Object putLock_ = new Object();

  /**
   * The last node of list. Put() appends to list, so modifies last_
   */
  protected LinkedNode<T> last_;

  /**
   * The number of threads waiting for a take.
   * Notifications are provided in put only if greater than zero.
   * The bookkeeping is worth it here since in reasonably balanced
   * usages, the notifications will hardly ever be necessary, so
   * the call overhead to notify can be eliminated.
   */
  protected int waitingForTake_ = 0;

  public LinkedQueue() {
    head_ = new LinkedNode<T>(null);
    last_ = head_;
  }

  /**
   * Main mechanics for put/offer *
   */
  protected void insert(T x) {
    synchronized (putLock_) {
      LinkedNode<T> p = new LinkedNode<T>(x);
      synchronized (last_) {
        last_.next = p;
        last_ = p;
      }
      if (waitingForTake_ > 0)
        putLock_.notify();
    }
  }

  /**
   * Main mechanics for take/poll *
   */
  protected synchronized T extract() {
    synchronized (head_) {
      T x = null;
      LinkedNode<T> first = head_.next;
      if (first != null) {
        x = first.value;
        first.value = null;
        head_ = first;
      }
      return x;
    }
  }


  public void put(T x) throws InterruptedException {
    if (x == null) throw new IllegalArgumentException();
    if (Thread.interrupted()) throw new InterruptedException();
    insert(x);
  }

  public boolean offer(T x, long msecs) throws InterruptedException {
    if (x == null) throw new IllegalArgumentException();
    if (Thread.interrupted()) throw new InterruptedException();
    insert(x);
    return true;
  }

  public T take() throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
    // try to extract. If fail, then enter wait-based retry loop
    T x = extract();
    if (x != null)
      return x;
    else {
      synchronized (putLock_) {
        try {
          ++waitingForTake_;
          for (; ;) {
            x = extract();
            if (x != null) {
              --waitingForTake_;
              return x;
            } else {
              putLock_.wait();
            }
          }
        } catch (InterruptedException ex) {
          --waitingForTake_;
          putLock_.notify();
          throw ex;
        }
      }
    }
  }

  public T peek() {
    synchronized (head_) {
      LinkedNode<T> first = head_.next;
      if (first != null)
        return first.value;
      else
        return null;
    }
  }


  public boolean isEmpty() {
    synchronized (head_) {
      return head_.next == null;
    }
  }

  public T poll(long msecs) throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
    T x = extract();
    if (x != null)
      return x;
    else {
      synchronized (putLock_) {
        try {
          long waitTime = msecs;
          long start = (msecs <= 0) ? 0 : System.currentTimeMillis();
          ++waitingForTake_;
          for (; ;) {
            x = extract();
            if (x != null || waitTime <= 0) {
              --waitingForTake_;
              return x;
            } else {
              putLock_.wait(waitTime);
              waitTime = msecs - (System.currentTimeMillis() - start);
            }
          }
        } catch (InterruptedException ex) {
          --waitingForTake_;
          putLock_.notify();
          throw ex;
        }
      }
    }
  }
}


