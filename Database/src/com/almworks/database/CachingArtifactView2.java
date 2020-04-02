package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.database.objects.DBUtil;
import com.almworks.util.Pair;
import com.almworks.util.collections.Containers;
import com.almworks.util.commons.Procedure;
import com.almworks.util.exec.ThreadGate;
import gnu.trove.TLongObjectHashMap;
import org.almworks.util.Collections15;
import org.almworks.util.RuntimeInterruptedException;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.DetachComposite;

import java.util.*;

public class CachingArtifactView2 extends AbstractArtifactView {
  private final SortedSet<Revision> myRevisionsCache = Collections15.treeSet(new RevisionComparator(false));
  private final ArtifactView myView;

  private final Object myLock = new Object();
  private Detach myCacherDetach = null;
  private WCN myCacheWCN = WCN.EARLIEST;

  public CachingArtifactView2(Basis basis, ArtifactView view) {
    super(basis, view.getRange(), view.getStrategy());
    myView = view;
  }

  public Set<Revision> filter(Set<Revision> source) {
    return myView.filter(source);
  }

  public Pair<WCN, Integer> countWithWCN() {
    return myView.countWithWCN();
  }

  public boolean isCountQuicklyAvailable() {
    return myView.isCountQuicklyAvailable();
  }

  public boolean isVisible(Revision revision, boolean strictStrategy) {
    return myView.isVisible(revision, strictStrategy);
  }

  public void verify(Procedure<Boolean> reportTo) {
    myView.verify(reportTo);
  }

  public Detach addListener(final ThreadGate callbackGate, WCN.Range requestedRange, final ArtifactListener listener) {
    assert listener != null;
    final WCN.Range range = WCN.intersect(myRange, requestedRange);
    if (range.isNil())
      return Detach.NOTHING;
    WCN.DividedRange dividedRange = WCN.divideRange(range, myBasis.getCurrentWCN());
    final WCN.Range past = dividedRange.getFirstRange();
    final WCN.Range future = dividedRange.getSecondRange();
    if (!listener.onListeningAcknowledged(past, future))
      return Detach.NOTHING;

    final DetachComposite detach = new DetachComposite(true);

    callbackGate.execute(new Runnable() {
      public void run() {
        try {
          final TLongObjectHashMap/*<Revision>*/ fired = new TLongObjectHashMap();
          if (past.isNil())
            scanPast(myBasis.getCurrentWCN());
          else
            scanPast(past.getEnd());
          boolean proceed = firePast(fired, past, listener);
          if (proceed)
            proceed = listener.onPastPassed(past);
          if (proceed)
            detach.add(fireFuture(fired, future, callbackGate, listener));
        } catch (InterruptedException e) {
          detach.detach();
          throw new RuntimeInterruptedException(e);
        }
      }
    });

    return detach;
  }

  public Filter getFilter() {
    return myView.getFilter();
  }

  private Detach fireFuture(final TLongObjectHashMap/*<Revision>*/ fired, final WCN.Range future,
    final ThreadGate callbackGate, final ArtifactListener listener)
  {
    if (future.isNil())
      return Detach.NOTHING;
    Detach detach = myView.addListener(callbackGate, future, new ArtifactListener() {
      private final SortedSet<Revision> myInterPastRevisions = Collections15.treeSet(new RevisionComparator(true));

      public boolean onListeningAcknowledged(WCN.Range past, WCN.Range future) {
        return true;
      }

      public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
        myInterPastRevisions.add(lastRevision);
        return true;
      }

      public boolean onPastPassed(WCN.Range pastRange) {
        try {
          WCN wcn = null;
          boolean proceed = true;
          for (Iterator<Revision> ii = myInterPastRevisions.iterator(); ii.hasNext();) {
            Revision revision = ii.next();
            if (wcn != null && wcn.isEarlier(revision.getWCN()))
              proceed = listener.onWCNPassed(wcn);
            if (!proceed)
              return false;
            wcn = revision.getWCN();
            proceed = onRevision(revision.getArtifact(), revision, false);
            if (!proceed)
              return false;
          }
          if (wcn != null)
            proceed = listener.onWCNPassed(wcn);
          return proceed;
        } finally {
          myInterPastRevisions.clear();
        }
      }

      public boolean onArtifactAppears(Artifact artifact, Revision lastRevision) {
        return onRevision(artifact, lastRevision, false);
      }

      public boolean onArtifactChanges(Artifact artifact, Revision prevRevision, Revision newRevision) {
        return onRevision(artifact, newRevision, false);
      }

      public boolean onArtifactDisappears(Artifact artifact, Revision lastSeenRevision, Revision unseenRevision) {
        return onRevision(artifact, unseenRevision, true);
      }

      public boolean onWCNPassed(WCN wcn) {
        return listener.onWCNPassed(wcn);
      }

      private boolean onRevision(Artifact artifact, Revision revision, boolean disappear) {
        Revision prevRevision;
        synchronized (fired) {
          if (disappear) {
            prevRevision = (Revision) fired.remove(artifact.getKey());
          } else {
            prevRevision = (Revision) fired.put(artifact.getKey(), revision);
          }
        }
        boolean proceed;
        if (prevRevision == null) {
          if (!disappear) {
            proceed = listener.onArtifactAppears(artifact, revision);
          } else {
            // we didn't fire anything but maybe we only listen the future?
            // see the comment in FilteredArtifactView
            prevRevision = DBUtil.getPrevRevisionForView(revision);
            boolean prevGood;
            synchronized (myLock) {
              prevGood = prevRevision != null && myRevisionsCache.contains(prevRevision);
            }
            if (prevGood) {
              proceed = listener.onArtifactDisappears(artifact, prevRevision, revision);
            } else {
              proceed = true;
            }
          }
        } else {
          if (!disappear) {
            proceed = listener.onArtifactChanges(artifact, prevRevision, revision);
          } else {
            proceed = listener.onArtifactDisappears(artifact, prevRevision, revision);
          }
        }
        return proceed;
      }
    });
    return detach;
  }

  private boolean firePast(TLongObjectHashMap/*<Revision>*/ fired, WCN.Range past, ArtifactListener listener)
    throws InterruptedException
  {
    if (!past.isNil()) {
      WCN pastEnd = past.getEnd();
      List<Revision> list = null;
      synchronized (myLock) {
        for (Revision revision : myRevisionsCache) {
          if (revision.getWCN().isEarlier(pastEnd)) {
            list = Collections15.arrayList(myRevisionsCache.tailSet(revision));
            break;
          }
        }
      }
      if (list != null && list.size() != 0) {
        for (Revision wantedRevision : list) {
          Artifact artifact = wantedRevision.getArtifact();
          long artifactKey = artifact.getKey();
          if (fired.containsKey(artifactKey))
            continue;
          Revision lastRevision = artifact.getLastRevision(pastEnd, myStrategy);
          if (lastRevision == null)
            continue;
          if (!wantedRevision.equals(lastRevision))
            continue;
          boolean proceed = listener.onArtifactExists(artifact, lastRevision);
          fired.put(artifactKey, lastRevision);
          if (!proceed)
            return false;
        }
      }
    }
    return true;
  }

  private void scanPast(WCN end) throws InterruptedException {
    synchronized (myLock) {
      if (myCacherDetach == null)
        myCacherDetach = listenView();
      while (myCacheWCN.isEarlier(end)) {
        myLock.wait();
      }
    }
  }

  private Detach listenView() {
    return myView.addListener(ThreadGate.LONG(new Object()), WCN.ETERNITY, new ArtifactListener.Adapter() {
      public boolean onArtifactAppears(Artifact artifact, Revision lastRevision) {
        return onRevision(lastRevision);
      }

      public boolean onArtifactChanges(Artifact artifact, Revision prevRevision, Revision newRevision) {
        return onRevision(newRevision);
      }

      public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
        return onRevision(lastRevision);
      }

      public boolean onPastPassed(WCN.Range pastRange) {
        synchronized (myLock) {
          myCacheWCN = WCN.createWCN(Math.max(pastRange.getEnd().getUCN(), myCacheWCN.getUCN()));
          myLock.notifyAll();
        }
        return true;
      }

      public boolean onWCNPassed(WCN wcn) {
        synchronized (myLock) {
          myCacheWCN = WCN.createWCN(Math.max(wcn.getUCN() + 1, myCacheWCN.getUCN()));
          myLock.notifyAll();
        }
        return true;
      }

      private boolean onRevision(Revision revision) {
        synchronized (myLock) {
          myRevisionsCache.add(revision);
        }
        return true;
      }
    });
  }

  public ArtifactView switchStrategy(RevisionAccess strategy) {
    return myStrategy == strategy ? this : new CachingArtifactView2(myBasis, myView.changeStrategy(strategy));
  }

  private static class RevisionComparator implements Comparator<Revision> {
    private final int myForward;

    public RevisionComparator(boolean forward) {
      myForward = forward ? 1 : -1;
    }

    public int compare(Revision r1, Revision r2) {
      if (r1 == r2)
        return 0;
      long dif = (r1.getWCN().compareTo(r2.getWCN())) * myForward;
      if (dif != 0)
        return dif < 0 ? -1 : 1;
      long k1 = r1.getKey();
      long k2 = r2.getKey();
      return Containers.compareLongs(k1, k2) * myForward;
    }
  }
}

