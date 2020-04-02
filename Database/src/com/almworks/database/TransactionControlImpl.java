package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.universe.Atom;
import com.almworks.util.Pair;
import com.almworks.util.events.FireEventSupport;
import com.almworks.util.events.ProcessingLock;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.*;

import java.util.Collections;
import java.util.SortedSet;

/**
 * :todoc:
 *
 * @author sereda
 */
class TransactionControlImpl implements TransactionControlExt {
  private static final boolean DISABLE_ISOLATED_NOTIFICATIONS = false;

  private final Basis myBasis;
  private final Object myGlobalTransactionLock = new Object();
  private final FireEventSupport<TransactionListener> myEventSupport =
    FireEventSupport.create(TransactionListener.class, myGlobalTransactionLock, false, null);
  private final PropertyMap myServiceMap =
    new PropertyMap(null, null, Collections.synchronizedMap(Collections15.<TypedKey<?>, Object>hashMap()));
  private WCN myLateWCN = WCN.EARLIEST;
  private volatile CommittedAtomsHook[] myHooks = null;
  private final SortedSet<Long> myWaitingUcns = Collections15.treeSet();

  private final Object myCommitLockLock = new Object();
  private ProcessingLock myCommitLock = ProcessingLock.DUMMY;
  private long myLastTransactionFinish = 0;

  public TransactionControlImpl(Basis basis) {
    myBasis = basis;
  }

  public Transaction beginTransaction() {
    TransactionImpl transaction = new TransactionImpl(myBasis, this);
    // pass all events to this event support too - with correct handling of getDispatcherSnapshot()
    transaction.getEventSource().addChainedSource(myEventSupport);
    return transaction;
  }

  public Pair<Detach,WCN> addListener(TransactionListener listener) {
    return addListener(ThreadGate.STRAIGHT, listener);
  }

  public WCN addListener(Lifespan lifespan, TransactionListener listener) {
    return addListener(lifespan, ThreadGate.STRAIGHT, listener);
  }

  public WCN addListener(Lifespan lifespan, ThreadGate gate, TransactionListener listener) {
    synchronized (myGlobalTransactionLock) {
      WCN wcn = myBasis.getCurrentWCN();
      myEventSupport.addListener(lifespan, gate, listener);
      return wcn;
    }
  }

  public Pair<Detach,WCN> addListener(ThreadGate gate, TransactionListener listener) {
    DetachComposite detach = new DetachComposite();
    WCN wcn = addListener(detach, gate, listener);
    return Pair.<Detach, WCN>create(detach, wcn);
  }

  public void removeListener(TransactionListener listener) {
    myEventSupport.removeListener(listener);
  }

  public TransactionListener getListenersSnapshot() {
    return myEventSupport.getDispatcherSnapshot();
  }

  public Object getGlobalLock() {
    return myGlobalTransactionLock;
  }

  public PropertyMap getTransactionServiceMap() {
    return myServiceMap;
  }

  public synchronized void onLateWCNPromotion(WCN commitWCN) {
    if (commitWCN != null) {
      long newUcn = commitWCN.getUCN() + 1;
      long lastUcn = myLateWCN.getUCN();
      if (newUcn == lastUcn + 1) {
        while (myWaitingUcns.size() > 0) {
          Long first = myWaitingUcns.first();
          long ucn = first.longValue();
          assert ucn > newUcn : newUcn + " " + ucn;
          if (ucn <= newUcn) {
            myWaitingUcns.remove(first);
          } else if (ucn == newUcn + 1) {
            myWaitingUcns.remove(first);
            newUcn = ucn;
          } else {
            break;
          }
        }
        myLateWCN = WCN.createWCN(newUcn);
      } else if (newUcn > lastUcn) {
        // wait
        myWaitingUcns.add(new Long(newUcn));
      } else {
        assert false : lastUcn + " " + newUcn;
      }
    }
  }

  public synchronized void setLateWCN(WCN earlyWCN) {
    assert myLateWCN == WCN.EARLIEST;
    myLateWCN = earlyWCN;
  }

  public void callCommittedAtomsHook(TransactionExt transaction, Atom[] atoms) {
    CommittedAtomsHook[] hooks = myHooks;
    if (hooks != null) {
      for (int i = 0; i < hooks.length; i++) {
        CommittedAtomsHook hook = hooks[i];
        if (hook != null)
          hook.onCommittedAtoms(transaction, atoms);
      }
    }
  }

  public synchronized void addCommittedAtomsHook(CommittedAtomsHook hook) {
    // there is only one hook actually, so don't bother for anything quick
    if (myHooks == null) {
      CommittedAtomsHook[] newHooks = new CommittedAtomsHook[1];
      newHooks[0] = hook;
      myHooks = newHooks;
    } else {
      CommittedAtomsHook[] newHooks = new CommittedAtomsHook[myHooks.length + 1];
      System.arraycopy(myHooks, 0, newHooks, 0, myHooks.length);
      newHooks[newHooks.length - 1] = hook;
      myHooks = newHooks;
    }
  }

  public ProcessingLock beginCommit(Transaction owner) throws InterruptedException {
    // holding global lock will prevent ArtifactViews from attaching/detaching, and that may lead to UI thread
    // stagnation and other commit lock will not be released.
    assert !Thread.holdsLock(myGlobalTransactionLock);
    synchronized (myCommitLockLock) {
      myCommitLock.waitRelease();
      myCommitLock = DISABLE_ISOLATED_NOTIFICATIONS ? ProcessingLock.DUMMY : new CommitLock(owner, myCommitLockLock);
      return myCommitLock;
    }
  }

  public synchronized void onTransactionFinished() {
    myLastTransactionFinish = System.currentTimeMillis();
  }

  public synchronized long getLastTransactionTime() {
    return myLastTransactionFinish;
  }

  public synchronized WCN getLateWCN() {
    assert myLateWCN != null;
    return myLateWCN;
  }
}

