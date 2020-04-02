package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.util.collections.Containers;
import com.almworks.util.exec.ThreadGate;
import gnu.trove.TLongObjectHashMap;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Detach;
import util.concurrent.Synchronized;
import util.concurrent.SynchronizedBoolean;

import java.util.SortedSet;

public class CachingFilteredArtifactView extends FilteredArtifactView {
  private static int ourInstances = 0;
  private final SortedSet<ArtifactInfo> myArtifactsCache = Collections15.treeSet();
  private final TLongObjectHashMap/*<ArtifactInfo>*/ myArtifactsCacheKeys = new TLongObjectHashMap();
  private Detach myCacheListener = null;
  private final Object myLock = new Object();

  public CachingFilteredArtifactView(Basis basis, ArtifactView parent, Filter filter, WCN.Range range) {
    super(basis, new FilteredArtifactView(basis, parent, filter, range), Filter.ALL, range);
    assert countStats(this);
  }

  private CachingFilteredArtifactView(Basis basis, FilteredArtifactView filteredView, WCN.Range range) {
    super(basis, filteredView, Filter.ALL, range);
  }

  public Detach addListener(final ThreadGate callbackGate, WCN.Range range, final ArtifactListener listener) {
    final WCN.Range listenedRange = WCN.intersect(myRange, range);
    if (listenedRange.isNil())
      return Detach.NOTHING;
    if (!listenedRange.equals(myRange)) {
      assert __debug("transferring to parent");
      return myParent.addListener(callbackGate, listenedRange, listener);
    }
    listenParent();
    long divider;
    final ArtifactInfo[] pastArtifacts;
    synchronized (myLock) {
      divider = myArtifactsCache.isEmpty() ? WCN.EARLIEST.getUCN() : myArtifactsCache.first().getLastUCN() + 1;
      pastArtifacts = myArtifactsCache.toArray(new ArtifactInfo[myArtifactsCache.size()]);
    }
    WCN.DividedRange dividedRange = WCN.divideRange(listenedRange, WCN.createWCN(divider));
    final WCN.Range past = dividedRange.getFirstRange();
    final WCN.Range future = dividedRange.getSecondRange();
    // todo violating contract - onListeningAcknowledged is called with incorrect WCNs
    assert __debug("call onListeningAcknowledged(" + past + ", " + future + ")");
    if (!listener.onListeningAcknowledged(past, future)) {
      return Detach.NOTHING;
    }
    final Object lock = new Object();
    final SynchronizedBoolean proceed = new SynchronizedBoolean(true, lock);
    final Synchronized<Detach> detach = new Synchronized<Detach>(null, lock);
    callbackGate.execute(new Runnable() {
      public void run() {
        for (final ArtifactInfo info : pastArtifacts) {
          boolean r = listener.onArtifactExists(info.getArtifact(), info.getLastRevision());
          if (!r) {
            proceed.commit(true, false);
            break;
          }
        }
        synchronized (lock) {
          if (!future.isNil() && proceed.get()) {
            // todo violating contract - onListeningAcknowledged is called with incorrect WCNs
            assert __debug("listening parent (" + future + ")(" + myParent + ")");
            detach.set(myParent.addListener(ThreadGate.STRAIGHT, future, new ArtifactListener.Decorator(listener) {
              public boolean onListeningAcknowledged(WCN.Range past, WCN.Range future) {
                return true;
              }

              public boolean onArtifactAppears(Artifact artifact, Revision lastRevision) {
                assert __debug("called onArtifactAppears A:" + artifact.getKey() + " R:" + lastRevision.getKey());

                // todo faster - hash
                long key = artifact.getKey();
                for (ArtifactInfo info : pastArtifacts) {
                  if (key == info.getKey()) {
                    return onArtifactChanges(artifact, info.getLastRevision(), lastRevision);
                  }
                }
                return super.onArtifactAppears(artifact, lastRevision);
              }

              public boolean onArtifactChanges(Artifact artifact, Revision prevRevision, Revision newRevision) {
                assert __debug("called onArtifactChanges A:" + artifact.getKey() + " R:" + prevRevision.getKey()
                  + " => R:" + newRevision.getKey());
                return super.onArtifactChanges(artifact, prevRevision, newRevision);
              }

              public boolean onArtifactDisappears(Artifact artifact, Revision lastSeenRevision,
                Revision unseenRevision)
              {
                assert __debug("called onArtifactDisappears A:" + artifact.getKey() + " R:" + lastSeenRevision.getKey()
                  + " => R:" + unseenRevision.getKey());
                return super.onArtifactDisappears(artifact, lastSeenRevision, unseenRevision);
              }

              public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
                assert __debug("called onArtifactExists A:" + artifact.getKey() + " R:" +  lastRevision.getKey());
                return super.onArtifactExists(artifact, lastRevision);
              }

              public boolean onPastPassed(WCN.Range pastRange) {
                assert __debug("called onPastPassed(" + pastRange + ")");
                return super.onPastPassed(pastRange);
              }

              public boolean onWCNPassed(WCN wcn) {
                return super.onWCNPassed(wcn);
              }
            }));
          }
        }
      }
    });
    Detach allDetach = new Detach() {
      protected void doDetach() {
        Detach parentDetach = null;
        synchronized (lock) {
          proceed.commit(true, false);
          parentDetach = detach.get();
          detach.set(null);
        }
        if (parentDetach != null)
          parentDetach.detach();
      }
    };
    return allDetach;
  }

  public String getDebugFilterString() {
    return ((FilteredArtifactView) myParent).getDebugFilterString();
  }

  public ArtifactView switchStrategy(RevisionAccess strategy) {
    assert myStrategy != strategy;
    assert myParent.getStrategy().equals(myStrategy);
    FilteredArtifactView parent = (FilteredArtifactView) myParent.changeStrategy(strategy);
    return new CachingFilteredArtifactView(myBasis, parent, myRange);
  }

  private void listenParent() {
    synchronized (myLock) {
      if (myCacheListener != null)
        return;
      myCacheListener = myParent.addListener(ThreadGate.STRAIGHT, myRange, new ArtifactListener.Adapter() {
        public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
          assert __debug("adding (exists) A:" + artifact.getKey() + " R:" + lastRevision.getKey());
          readd(artifact, lastRevision);
          return true;
        }

        public boolean onArtifactAppears(Artifact artifact, Revision lastRevision) {
          assert __debug("adding (appears) A:" + artifact.getKey() + " R:" + lastRevision.getKey());
          readd(artifact, lastRevision);
          return true;
        }

        public boolean onArtifactDisappears(Artifact artifact, Revision lastSeenRevision, Revision unseenRevision) {
          assert __debug("removing (disappears) A:" + artifact.getKey() + " R:" + lastSeenRevision.getKey() + " => R:"
            + unseenRevision.getKey());
          remove(artifact);
          return true;
        }

        public boolean onArtifactChanges(Artifact artifact, Revision prevRevision, Revision newRevision) {
          assert __debug("adding (changes) A:" + artifact.getKey() + " R:" + prevRevision.getKey() + " => R:" +
            newRevision.getKey());
          readd(artifact, newRevision);
          return true;
        }
      });
    }
  }

  private void remove(Artifact artifact) {
    synchronized (myLock) {
      ArtifactInfo oldInfo = (ArtifactInfo) myArtifactsCacheKeys.remove(artifact.getKey());
      if (oldInfo != null) {
        myArtifactsCache.remove(oldInfo);
      }
    }
  }

  private void readd(Artifact artifact, Revision lastRevision) {
    synchronized (myLock) {
      ArtifactInfo info = new ArtifactInfo(artifact, lastRevision);
      ArtifactInfo oldInfo = (ArtifactInfo) myArtifactsCacheKeys.get(info.getKey());
      if (oldInfo != null)
        myArtifactsCache.remove(oldInfo);
      myArtifactsCache.add(info);
      myArtifactsCacheKeys.put(info.getKey(), info);
    }
  }

  private static boolean countStats(final CachingFilteredArtifactView instance) {
    ThreadGate.LONG(CachingFilteredArtifactView.class).execute(new Runnable() {
      public void run() {
        synchronized (CachingFilteredArtifactView.class) {
          ourInstances++;
//          System.out.println("CachingFilteredArtifactView: " + ourInstances + " " + instance.getDebugFilterString());
        }
      }
    });
    return true;
  }

  private static final class ArtifactInfo implements Comparable<ArtifactInfo> {
    private final long myKey;
    private final Artifact myArtifact;
    private final long myLastUCN;
    private final Revision myLastRevision;

    public ArtifactInfo(Artifact artifact, Revision lastRevision) {
      assert artifact != null;
      assert lastRevision != null;
      myArtifact = artifact;
      myKey = artifact.getKey();
      myLastRevision = lastRevision;
      myLastUCN = lastRevision != null ? lastRevision.getWCN().getUCN() : 0;
    }

    public int compareTo(ArtifactInfo that) {
      int c = Containers.compareLongs(that.myLastUCN, myLastUCN);
      if (c != 0) {
        return c;
      } else {
        return Containers.compareLongs(that.myKey, myKey);
      }
    }

    public long getLastUCN() {
      return myLastUCN;
    }

    public Revision getLastRevision() {
      return myLastRevision;
    }

    public Artifact getArtifact() {
      return myArtifact;
    }

    public int hashCode() {
      return (int) myKey;
    }

    public boolean equals(Object o) {
      if (!(o instanceof ArtifactInfo))
        return false;
      return myKey == ((ArtifactInfo) o).myKey;
    }

    public long getKey() {
      return myKey;
    }
  }
}

