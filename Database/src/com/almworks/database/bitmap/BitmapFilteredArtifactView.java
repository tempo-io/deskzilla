package com.almworks.database.bitmap;

import com.almworks.api.database.*;
import com.almworks.database.*;
import com.almworks.database.filter.SystemFilter;
import com.almworks.database.objects.DBUtil;
import com.almworks.database.objects.PhysicalRevisionIterator;
import com.almworks.util.Pair;
import com.almworks.util.commons.*;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.*;
import org.almworks.util.*;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.DetachComposite;
import util.external.BitSet2;

import java.util.Collection;
import java.util.Set;

public class BitmapFilteredArtifactView extends FilteredArtifactView implements ViewHavingBitmap {
  private Lazy<CompositeBitmapIndex> myIndex = new Lazy<CompositeBitmapIndex>() {
    public CompositeBitmapIndex instantiate() {
      try {
        return CompositeBitmapIndex.create(myBasis.getBitmapIndexManager(), myFilter, myStrategy);
      } catch (CompositeBitmapIndex.IncompatibleFilter e) {
        Log.warn("no index", e);
        //noinspection ConstantConditions
        return null;
      }
    }
  };

  private final Lazy<AbstractArtifactView> myBackupView = new Lazy<AbstractArtifactView>() {
    public AbstractArtifactView instantiate() {
      return new CachingFilteredArtifactView(myBasis, myParent, myFilter, myRange);
    }
  };
  private static final int MAX_ATTEMPTS = 3;

  private CorruptIndexRebuilder myCorruptRebuilder;

  private static final BottleneckJobs<Pair<BitmapFilteredArtifactView, Procedure<Boolean>>> ourBitmapVerifier =
    new BottleneckJobs<Pair<BitmapFilteredArtifactView, Procedure<Boolean>>>(1000,
      ThreadGate.LONG(BitmapFilteredArtifactView.class))
    {
      protected void execute(Pair<BitmapFilteredArtifactView, Procedure<Boolean>> job) {
        try {
          BitmapFilteredArtifactView view = job.getFirst();
          Procedure<Boolean> reportTo = job.getSecond();

          long now = System.currentTimeMillis();
          if (now < view.myBasis.ourTransactionControl.getLastTransactionTime() + 1000) {
            ourBitmapVerifier.addJobDelayed(job);
            return;
          }

          boolean result = view.verifyBitmap();
          if (reportTo != null) {
            reportTo.invoke(result);
          }
        } catch (InterruptedException e) {
          Log.warn("rebuilding indexes has been interrupted, drop indexes manually");
          Thread.currentThread().interrupt();
        }
      }
    };

  @CanBlock
  private boolean verifyBitmap() throws InterruptedException {
    myBasis.ourBitmapCheckerState.setState("Verifying indexes\u2026");
    try {
      boolean corrupt = isBitmapCorrupt();
      if (corrupt) {
        myBasis.ourBitmapCheckerState.setState("Rebuilding indexes\u2026");
        rebuildCorruptIndexes();
      }
      return !corrupt;
    } finally {
      myBasis.ourBitmapCheckerState.setState(null);
    }
  }

  private boolean isBitmapCorrupt() {
    Function<Collection<Revision>, Boolean> delayer = new Function<Collection<Revision>, Boolean>() {
      private long myTime = System.currentTimeMillis();
      private final long WORK = 300;
      private final long REST = 700;

      public Boolean invoke(Collection<Revision> arg) {
        try {
          long now = System.currentTimeMillis();
          if (now - myTime > WORK) {
            Thread.sleep(REST);
            myTime = System.currentTimeMillis();
          }
        } catch (InterruptedException e) {
          throw new RuntimeInterruptedException(e);
        }
        return true;
      }
    };

    Collection<Revision> bitmapped = getAllArtifacts(delayer);
    Collection<Revision> plain = getPlainFilteredView().getAllArtifacts(delayer);

    if (bitmapped.size() == 0 && plain.size() == 0)
      return false;
    Set<Long> under = Collections15.hashSet();
    Set<Long> over = Collections15.hashSet();
    for (Revision r : plain) {
      under.add(r.getKey());
    }
    for (Revision r : bitmapped) {
      long id = r.getKey();
      boolean removed = under.remove(id);
      if (!removed) {
        over.add(id);
      }
    }
    if (under.size() > 0 || over.size() > 0) {
      Log.warn("index corrupt (" + under.size() + ", " + over.size() + ") in " + this);
      return true;
    } else {
      return false;
    }
  }

  public BitmapFilteredArtifactView(Basis basis, AbstractArtifactView parent, Filter filter, WCN.Range range) {
    super(basis, parent, filter, range);
    assert parent instanceof ViewHavingBitmap;
    assert filter == Filter.ALL || filter == Filter.NONE || filter instanceof SystemFilter;
  }

  public ArtifactView switchStrategy(RevisionAccess strategy) {
    assert myStrategy != strategy;
    assert myParent.getStrategy().equals(myStrategy);
    ArtifactView parent = myParent.changeStrategy(strategy);
    assert parent.getStrategy().equals(strategy);
    return new BitmapFilteredArtifactView(myBasis, (AbstractArtifactView) parent, myFilter, myRange);
  }

  public Pair<WCN, Integer> countWithWCN() {
    try {
      BitSet2 bits = getRevisionBits();
      return Pair.create(WCN.LATEST, bits.cardinality());
    } catch (InterruptedException e) {
      Log.warn(this + " interrupted", e);
      throw new RuntimeInterruptedException(e);
    }
  }

  public boolean isCountQuicklyAvailable() {
    return hasAllIndices();
  }

  public void verify(Procedure<Boolean> reportTo) {
    ourBitmapVerifier.addJob(Pair.create(this, reportTo));
  }

  public Detach addListener(final ThreadGate callbackGate, WCN.Range range, final ArtifactListener listener) {
    final WCN.Range listenedRange = WCN.intersect(myRange, range);
    if (listenedRange.isNil())
      return Detach.NOTHING;
    WCN wcn = myBasis.getCurrentWCN();
    if (listenedRange.getEnd().isEarlier(wcn)) {
      return getBackupView().addListener(callbackGate, range, listener);
    }

    final WCN.DividedRange pastAndFuture = WCN.divideRange(listenedRange, wcn);
    if (!listener.onListeningAcknowledged(pastAndFuture.getFirstRange(), pastAndFuture.getSecondRange()))
      return Detach.NOTHING;

    __debugAddListener(listener);

    final DetachComposite detach = new DetachComposite(true);
    callbackGate.execute(new Runnable() {
      public void run() {
//        TLongObjectHashMap/*<Revision>*/ firedMap; // todo javolution map?
        BitSet2 fired;
        boolean proceed = true;

        if (pastAndFuture.getFirstRange().isNil()) {
          fired = null;
        } else {
          BitSet2 result = null;
          fired = new BitSet2();
          boolean success = false;
          int attempt = 0;
          try { // InterruptedException
            while (proceed && !success && attempt < MAX_ATTEMPTS) {
              if (detach.isEnded()) {
                return;
              }
              attempt++;
              try {
                result = getRevisionBits();

                for (int i = result.prevSetBit(result.size()); i >= 0; i = result.prevSetBit(i - 1)) {
                  if (detach.isEnded()) {
                    return;
                  }
                  if (!proceed)
                    break;
                  assert myBasis.isRevisionAtom(myBasis.ourUniverse.getAtom(i));
                  Revision revision = myBasis.getRevision(i, PhysicalRevisionIterator.INSTANCE);
                  Artifact artifact = myBasis.getArtifactByRevision(i, CorruptIndexWrapper.INSTANCE);
                  long artifactKey = artifact.getKey();
                  if (fired.get((int) artifactKey))
                    continue;

                  if (!artifact.hasRCBExtension()) {
                    if (myStrategy != RevisionAccess.ACCESS_DEFAULT)
                      continue;
                  } else {
                    RevisionChain chain = artifact.getChain(myStrategy);
                    Revision rev = chain.getRevisionOnChainOrNull(revision);
                    revision = rev != null ? rev : chain.getLastRevisionOrNull(listenedRange.getEnd());
                  }

                  if (revision == null)
                    continue;
/*
// this is not needed
                  if (revision.getWCN().isEarlier(listenedRange.getStart()))
                    continue;
*/
                  proceed = listener.onArtifactExists(artifact, revision);
                  fired.set((int) artifactKey);
                }
                success = true;
              } catch (CorruptIndexException e) {
                Log.warn("index corrupt, rebuilding [" + Thread.currentThread().getName() + "]", e);
                rebuildCorruptIndexes();
              }
            }
            if (proceed && !success)
              throw new Failure("cannot build indexes");
          } catch (InterruptedException e) {
            Log.warn(this + " interrupted", e);
            try {
              detach.detach();
            } catch (Throwable ee) {
              // ignore
            }
            return;
          }
        }

        if (!proceed)
          return;

        fired = null;

        proceed = listener.onPastPassed(pastAndFuture.getFirstRange());
        if (!proceed) {
          return;
        }

        MyFutureListener futureListener = new MyFutureListener(listener, listenedRange);
        detach.add(myParent.addListener(callbackGate, pastAndFuture.getSecondRange(), futureListener));
      }
    });
    return detach;
  }

  public void rebuildCorruptIndexes() throws InterruptedException {
    myCorruptRebuilder = new CorruptIndexRebuilder(myBasis, myIndex, (AbstractArtifactView) myParent);
    myCorruptRebuilder.rebuildCorruptIndexes();
  }

  private AbstractArtifactView getBackupView() {
    return myBackupView.get();
  }

  public /*not synchronized*/BitSet2 getRevisionBits() throws InterruptedException {
    Threads.assertLongOperationsAllowed();
// lock updates to indexes so that part of indexes don't access updated in between
    BitmapIndexManager indexManager = myBasis.getBitmapIndexManager();
    indexManager.lockRead();
    try {
      BitSet2 sourceBits = ((ViewHavingBitmap) myParent).getRevisionBits();
      CompositeBitmapIndex index = myIndex.get();
      return index.filterBits(sourceBits);
    } finally {
      indexManager.unlockRead();
    }
  }

  public boolean hasAllIndices() {
    Threads.assertLongOperationsAllowed();
    boolean b = ((ViewHavingBitmap) myParent).hasAllIndices();
    if (!b)
      return false;
    if (myIndex.isInitialized())
      return true;
    return CompositeBitmapIndex.hasAllIndices(myBasis.getBitmapIndexManager(), myFilter, myStrategy);
  }

  private class MyFutureListener extends FilteringListener {
//    private TLongObjectHashMap/*<Revision>*/ myFiredMap;
    private final WCN.Range myListenedRange;

    public MyFutureListener(ArtifactListener listener, WCN.Range listenedRange /*TLongObjectHashMap firedMap*/) {
      super(listener, listenedRange.getEnd(), listenedRange.getStart());
      myListenedRange = listenedRange;
//      myFiredMap = firedMap;
    }

    public boolean onPastPassed(WCN.Range pastRange) {
      // optimization - after this past passed we do not need myFiredMap
//      TLongObjectHashMap/*<Revision>*/ map = myFiredMap;
//      if (map != null) {
//        map.clear();
//        myFiredMap = null;
//      }
      return true;
    }

    public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
      return __debugWrap(onRevision(artifact, lastRevision));
    }

    public boolean onArtifactAppears(Artifact artifact, Revision lastRevision) {
      return __debugWrap(onRevision(artifact, lastRevision));
    }

    public boolean onArtifactDisappears(Artifact artifact, Revision lastSeenRevision, Revision unseenRevision) {
//      TLongObjectHashMap/*<Revision>*/ map = myFiredMap;
//      if (map != null)
//        map.remove(artifact.getKey());
      return super.onArtifactDisappears(artifact, lastSeenRevision, unseenRevision);
    }

    private boolean onRevision(Artifact artifact, Revision lastRevision) {
//      TLongObjectHashMap/*<Revision>*/ map = myFiredMap;
//      Revision firedRevision = map == null ? null : (Revision) map.get(artifact.getKey());
//      if (firedRevision == null) {
        Revision prevRevision = DBUtil.getPrevRevisionForView(lastRevision);
        if (prevRevision != null) {
          if (/*prevRevision.getWCN().isEarlierOrEqual(myListenedRange.getStart())*/ !isVisible(prevRevision, false))
            return super.onArtifactAppears(artifact, lastRevision);
          else
            return super.onArtifactChanges(artifact, prevRevision, lastRevision);
        } else {
          return super.onArtifactAppears(artifact, lastRevision);
        }
//      }
//      if (firedRevision != null /*&& !lastRevision.equals(revision)*/)
//        return super.onArtifactChanges(artifact, firedRevision, lastRevision);
//      else
//        return super.onArtifactAppears(artifact, lastRevision);
    }
  }
}
