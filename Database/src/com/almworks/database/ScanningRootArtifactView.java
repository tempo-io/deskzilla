package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Index;
import com.almworks.database.objects.DBUtil;
import com.almworks.database.schema.Schema;
import com.almworks.util.DECL;
import com.almworks.util.Pair;
import com.almworks.util.commons.Lazy;
import com.almworks.util.commons.Procedure;
import com.almworks.util.events.LockOwner;
import com.almworks.util.events.ProcessingLock;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.Threads;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.almworks.util.detach.Detach;
import util.concurrent.SynchronizedBoolean;

import java.util.Iterator;
import java.util.Set;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ScanningRootArtifactView extends AbstractArtifactView {
  private final RootViewProvider myRootProvider;
  private final Lazy<Index> myArtifactIndex = new Lazy<Index>() {
    public Index instantiate() {
      return myBasis.getIndex(Schema.INDEX_ARTIFACTS);
    }
  };

  public ScanningRootArtifactView(Basis basis, RootViewProvider provider, RevisionAccess strategy) {
    super(basis, WCN.ETERNITY, strategy);
    assert provider != null;
    myRootProvider = provider;
  }

  public Set<Revision> filter(Set<Revision> source) {
    // todo
    return source;
  }

  public boolean isCountQuicklyAvailable() {
    return false;
  }

  public boolean isVisible(Revision revision, boolean strictStrategy) {
    return true;
  }

  public Detach addListener(ThreadGate callbackGate, WCN.Range range, final ArtifactListener listener) {
    DECL.assumeThreadMayBeAWT();
    if (range.isNil())
      return Detach.NOTHING;

    final RootListenerJob job = createJob(callbackGate, listener);
    Pair<Detach, WCN> listenerInfo = myBasis.ourTransactionControl.addListener(job);
    WCN.DividedRange dividedRange = WCN.divideRange(WCN.ETERNITY, listenerInfo.getSecond());
    final WCN.Range past = WCN.intersect(dividedRange.getFirstRange(), range);
    final WCN.Range future = WCN.intersect(dividedRange.getSecondRange(), range);
    if (!listener.onListeningAcknowledged(past, future)) {
      job.cleanUp();
      return Detach.NOTHING;
    }
    job.scanPast(past, future);
    return new Detach() {
      protected void doDetach() {
        job.cleanUp();
      }
    };
  }

  public Filter getFilter() {
    return null;
  }

  protected RootListenerJob createJob(ThreadGate callbackGate, ArtifactListener listener) {
    return new RootListenerJob(callbackGate, listener);
  }

  public ArtifactView switchStrategy(RevisionAccess strategy) {
    return strategy == myStrategy ? this : myRootProvider.getRootView(strategy);
  }

  public void verify(Procedure<Boolean> reportTo) {
  }

  protected class RootListenerJob extends TransactionListener.Adapter {
    private final ThreadGate myCallbackGate;
    private volatile ArtifactListener myListener;
    private int myReentrancyLock = 1; // initially set to 1 to allow past first
    private final Object myDeferredLock = new Object();
    private Set<Artifact> myDeferred = null;

    // todo eats a lot
//    private final Map<Artifact.Key, Revision> myFiredRevisions = Collections15.hashMap();
//    private final Map<Artifact.Key, Integer> myFiredIncarnations = Collections15.hashMap();
//    private final Object myFiredRevisionsLock = new Object();
    protected final SynchronizedBoolean myTerminated = new SynchronizedBoolean(false);
    private boolean myInPast = true;

    private long myLowVisibilityEdge;
    private long myHighVisibilityEdge;

    public RootListenerJob(ThreadGate callbackGate, ArtifactListener listener) {
      if (listener == null)
        throw new NullPointerException("listener");
      myListener = listener;
      if (callbackGate == null)
        throw new NullPointerException("callbackGate");
      myCallbackGate = callbackGate;
    }

    public void onCommit(Transaction transaction, Set<Artifact> artifacts, ProcessingLock processingLock) {
      myHighVisibilityEdge = transaction.getCommitWCN().getUCN() - 1;
      for (Artifact artifact : artifacts) {
        process(artifact, processingLock);
      }
      signalWCN(transaction, processingLock);
    }

    private void signalWCN(final Transaction transaction, final ProcessingLock processingLock) {
      final ArtifactListener listener = myListener;
      final WCN commitWCN = transaction.getCommitWCN();
      // todo check transaction's toString()
      assert commitWCN != null : this + " " + transaction;
      if (listener != null && commitWCN.isEarlierOrEqual(myRange.getEnd())) {
        // todo cpu!
        final LockOwner lock = new LockOwner("GW", listener);
        processingLock.lock(lock);
        myCallbackGate.execute(new Runnable() {
          public void run() {
            try {
              boolean proceed = listener.onWCNPassed(commitWCN);
              if (!proceed) {
                cleanUp();
              }
            } finally {
              processingLock.release(lock);
            }
          }
        });
      }
    }

    private void process(Artifact artifact, ProcessingLock processingLock) {
      assert artifact == null || __debug("processing A:" + artifact.getKey());
      Artifact[] awaitingFire = null;
      boolean perform = true;
      while (perform && !myTerminated.get()) {
        synchronized (myDeferredLock) {
          if (myReentrancyLock > 0) {
            if (artifact != null) {
              if (myDeferred == null)
                myDeferred = Collections15.hashSet();
              myDeferred.add(artifact);
            }
            return;
          }
          if (myDeferred != null && myDeferred.size() > 0) {
            awaitingFire = myDeferred.toArray(new Artifact[myDeferred.size()]);
            myDeferred.clear();
          }
          myReentrancyLock++;
        }

        try {
          if (awaitingFire != null)
            for (Artifact anAwaitingFire : awaitingFire) {
              if (myTerminated.get())
                break;
              fireFromFuture(anAwaitingFire, processingLock);
            }
          if (!myTerminated.get() && artifact != null) {
            fireFromFuture(artifact, processingLock);
          }
        } finally {
          synchronized (myDeferredLock) {
            myReentrancyLock--;
            perform = perform && myDeferred != null && myDeferred.size() > 0;
          }
        }
      }
      if (myTerminated.get())
        cleanUp();
    }

    private void fireFromFuture(final Artifact artifact, final ProcessingLock processingLock) {
      assert!myInPast;
      final ArtifactListener listener = myListener;
      if (listener == null)
        return;
      final LockOwner lock = new LockOwner("GF", listener);
      processingLock.lock(lock);
      myCallbackGate.execute(new Runnable() {
        public void run() {
          try {
            doFireFromFuture(artifact, listener, processingLock);
          } finally {
            processingLock.release(lock);
          }
        }
      });
    }

    protected void doFireFromFuture(final Artifact artifact, final ArtifactListener listener,
      ProcessingLock processingLock)
    {
      boolean proceed = false;
//          debugNotifyObject(revision.getArtifact().getKey());
//      final Artifact.Key key = artifact.getKey();
      if (!artifact.isAccessStrategySupported(myStrategy)) {
        return;
      }

      Revision revision = artifact.getLastRevision(myRange.getEnd(), myStrategy);
      Revision prevRevision = DBUtil.getPrevRevisionForView(revision);

      assert myHighVisibilityEdge != 0 : this;

      boolean hasNews = hasNews(revision, prevRevision);
      if (!hasNews)
        return;

//      Revision prevRevision;
//      synchronized (myFiredRevisionsLock) {
//        prevRevision = myFiredRevisions.put(key, revision);
//        RCBArtifact rcb = artifact.getExtension(RCBArtifact.class);
//        boolean changedIncarnations = false;
//        if (rcb != null) {
//          Integer newIncarnation = rcb.getIncarnation();
//          Integer oldIncarnation = myFiredIncarnations.put(key, newIncarnation);
//          changedIncarnations = oldIncarnation != null && !oldIncarnation.equals(newIncarnation);
//        }
//        if (prevRevision != null && prevRevision.equals(revision) && !changedIncarnations) {
//            return;
//        }
//      }
      if (prevRevision == null) {
        proceed = listener.onArtifactAppears(artifact, revision);
      } else {
        proceed = listener.onArtifactChanges(artifact, prevRevision, revision);
      }
      if (!proceed)
        cleanUp();
    }

    private boolean hasNews(Revision revision, Revision prevRevision) {
      long revUcn = revision.getWCN().getUCN();
      if (revUcn <= myHighVisibilityEdge && revUcn >= myLowVisibilityEdge) {
        Revision rawPrev = revision.getPrevRevision();
        if (rawPrev == null)
          rawPrev = prevRevision;
        if (rawPrev != null) {
          long prevUcn = rawPrev.getWCN().getUCN();
          if (prevUcn <= myHighVisibilityEdge) {
            // check also for reincarnation
            RCBArtifact rcb = revision.getArtifact().getRCBExtension(false);
            if (rcb == null) {
              // an unchanged artifact
              return false;
            }
            long incarnationUcn = rcb.getLastIncarnationUcn();
            if (incarnationUcn <= myHighVisibilityEdge) {
              // was reincarnated in an earlier transaction
              return false;
            }
          }
        }
      }
      return true;
    }

/*
    private void debugNotifyObject(Artifact.Key key) {
      if (true)
        return;
      synchronized (RootArtifactView.class) {
        if (key.getLongRepresentation() != 107)
          return;
        if (__first == null)
          __first = this;
        else if (this != __first)
          return;
        boolean b;
        synchronized (myFiredRevisionsLock) {
          b = myFiredRevisions.containsKey(key);
        }
        final String message = "[" + this + "] [" + Thread.currentThread() + "] DEBUG: " + b;
        System.out.println(message);
        new Throwable().printStackTrace();
//        __debugMap.put(Thread.currentThread(), message);
      }
    }
*/

    protected boolean doFireFromPast(final Artifact artifact, Revision detectedRevision, WCN.Range range) {
      assert myInPast;
      final ArtifactListener listener = myListener;
      if (listener == null)
        return false;
      if (!artifact.isAccessStrategySupported(myStrategy))
        return true;

      WCN end = range.getEnd();
      if (detectedRevision != null && !detectedRevision.getWCN().isEarlier(end)) {
        detectedRevision = null;
      }
      final Revision revision =
        detectedRevision != null ? detectedRevision : artifact.getChain(myStrategy).getLastRevisionOrNull(end);

      if (revision == null)
        return true;

      if (revision.getWCN().getUCN() < range.getStartUCN())
        return true;

//      final Artifact.Key key = artifact.getKey();
      boolean added;

//      synchronized (myFiredRevisionsLock) {
//        added = !myFiredRevisions.containsKey(key);
//        if (added) {
//          myFiredRevisions.put(key, revision);
//          RCBArtifact rcb = artifact.getExtension(RCBArtifact.class);
//          if (rcb != null)
//            myFiredIncarnations.put(key, rcb.getIncarnation());
//        }
//      }

      added = true;
      if (added) {
        myCallbackGate.execute(new Runnable() {
          public void run() {
            if (!listener.onArtifactExists(artifact, revision))
              cleanUp();
          }
        });
      }
      return true;
    }

    private void scanPast(final WCN.Range past, final WCN.Range future) {
      if (past.isNil()) {
        myLowVisibilityEdge = future.getStartUCN() - 1;
        pastPassed(past, future);
      } else {
        myLowVisibilityEdge = past.getStartUCN();
        myCallbackGate.execute(new Runnable() {
          public void run() {
            doScanPast(past, future);
          }
        });
      }
    }

    protected void doScanPast(final WCN.Range past, final WCN.Range future) {
      Threads.assertLongOperationsAllowed();
      boolean proceed = true; //todo
      long endUCN = past.getEndUCN();
      Index index = myArtifactIndex.get();
      for (Iterator<Atom> iterator = index.all(); iterator.hasNext();) {
        if (myTerminated.get())
          break;
        Atom atom = iterator.next();
        long ucn = atom.getUCN();
        if (ucn >= endUCN)
          continue;
        if (!myBasis.isArtifactAtom(atom)) {
          Log.warn("artifact index provided non-artifact atom " + atom);
          continue;
        }
        Artifact artifact = myBasis.getArtifact(atom.getAtomID());
        if (!artifact.isAccessStrategySupported(myStrategy))
          continue;

        // first revision may appear later than artifact - so check that it is not later than endUCN
        Revision firstRevision = artifact.getChain(myStrategy).getFirstRevision();
        if (firstRevision == null) {
          Log.warn("no first revision (" + myStrategy + ") for artifact " + artifact);
          continue;
        }
        if (firstRevision.getWCN().getUCN() >= endUCN)
          continue;

        proceed = doFireFromPast(artifact, null, past);
        if (!proceed)
          break;
      }

      if (!proceed)
        cleanUp();
      pastPassed(past, future);
    }

    protected void pastPassed(final WCN.Range past, WCN.Range future) {
      assert myInPast;
      myInPast = false;
      final ArtifactListener listener = myListener;
      if (listener != null)
        myCallbackGate.execute(new Runnable() {
          public void run() {
            if (!listener.onPastPassed(past))
              cleanUp();
          }
        });

      if (future.isNil())
        cleanUp();

      synchronized (myDeferredLock) {
        myReentrancyLock--;
      }

      process(null, ProcessingLock.DUMMY);
    }

    public void cleanUp() {
      myTerminated.set(true);
      myListener = null;
      myBasis.ourTransactionControl.removeListener(this);
      synchronized (myDeferredLock) {
        if (myDeferred != null) {
          myDeferred.clear();
          myDeferred = null;
        }
      }
//      synchronized (myFiredRevisionsLock) {
//        myFiredRevisions.clear();
//        myFiredIncarnations.clear();
//      }
    }

    public String toString() {
      return "RLJ";
    }
  }

//  private static RootListenerJob __first = null;
//  public static final Map<Thread, String> __debugMap = Collections15.syncHashMap();
}
