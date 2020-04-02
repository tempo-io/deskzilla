package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.database.bitmap.BitmapFilteringPolicy;
import com.almworks.util.DECL;
import com.almworks.util.Pair;
import com.almworks.util.commons.Function;
import com.almworks.util.commons.Lazy;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.ArrayListCollectionModel;
import com.almworks.util.threads.Threads;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.DetachComposite;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public abstract class AbstractArtifactView implements ArtifactView {
  public static final boolean __debugView = false;

  protected final Basis myBasis;
  protected final WCN.Range myRange;
  protected final RevisionAccess myStrategy;

  protected final ViewFilteringPolicy myFilteringPolicy = new ViewCachePolicy(this, new BitmapFilteringPolicy());
  /**
   * todo make caching view?
   * initially, backup policy was creating caching artifact view, but both implementations are not buggy,
   * and since backup policy is applied rather rarely, we do not bother of fixing these implementations yet 
   */
  protected final Lazy<ViewFilteringPolicy> myBackupFilteringPolicy = new Lazy<ViewFilteringPolicy>() {
    public ViewFilteringPolicy instantiate() {
      return new ViewCachePolicy(AbstractArtifactView.this, DumbFilteringPolicy.INSTANCE);
    }
  };

/*
  protected final Lazy<ViewFilteringPolicy> myBackupFilteringPolicy = new Lazy<ViewFilteringPolicy>() {
    public ViewFilteringPolicy create() {
      return new ViewCachePolicy(AbstractArtifactView.this, new CachingViewPolicy(DumbFilteringPolicy.INSTANCE));
    }
  };
*/
//  protected final ViewFilteringPolicy myFilteringPolicy = myBackupFilteringPolicy.access();

  protected volatile String __debugName;

  protected AbstractArtifactView(Basis basis, WCN.Range range, RevisionAccess accessStrategy) {
    if (basis == null)
      throw new NullPointerException("basis");
    if (range == null)
      throw new NullPointerException("range");

    myBasis = basis;
    myRange = range;
    myStrategy = accessStrategy;
  }

  public Detach addListenerFuture(ThreadGate callbackGate, ArtifactListener listener) {
    return addListener(callbackGate, myBasis.getFuture(), listener);
  }

  public Detach addListenerPast(ThreadGate callbackGate, ArtifactListener listener) {
    return addListener(callbackGate, myBasis.getPast(), listener);
  }

  public void setDebugName(String debugName) {
    __debugName = debugName;
  }

  public void callWhenPassed(final WCN waitWcn, ThreadGate gate, final Runnable runnable) {
    final DetachComposite once = new DetachComposite(true);
    once.add(addListener(gate, WCN.ETERNITY, new ArtifactListener.Adapter() {
      public boolean onPastPassed(WCN.Range pastRange) {
        return onWCNPassed(pastRange.getEnd());
      }

      public boolean onWCNPassed(WCN wcn) {
        if (waitWcn.isEarlierOrEqual(wcn)) {
          once.detach();
          runnable.run();
          return false;
        } else {
          return false;
        }
      }
    }));
  }

  public ArtifactView filter(Filter filter) {
    return filter(filter, WCN.ETERNITY);
  }

  public ArtifactView filter(Filter filter, WCN.Range range) {
    return myFilteringPolicy.filter(this, filter, range);
  }

  public final ArtifactView changeStrategy(RevisionAccess strategy) {
    if (myStrategy.equals(strategy))
      return this;
    else
    //return new FilteredArtifactView(myBasis, this, Filter.ALL, myRange, strategy);
      return switchStrategy(strategy);
  }

  public abstract ArtifactView switchStrategy(RevisionAccess strategy);

  public Snapshot takeSnapshot() {
    DECL.assumeThreadMayBeAWT();
    final ArrayListCollectionModel<Revision> model = ArrayListCollectionModel.create(false, false);
    final WCN[] wcn = {null};
    addListener(ThreadGate.LONG(model), myBasis.getPast(), new ArtifactListener.Adapter() {
      public boolean onListeningAcknowledged(WCN.Range past, WCN.Range future) {
        wcn[0] = past.getEnd();
        return true;
      }

      public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
        Revision revision = artifact.getChain(myStrategy).getLastRevisionOrNull(wcn[0]);
        if (revision != null)
          model.getWritableCollection().add(revision);
        return true;
      }

      public boolean onPastPassed(WCN.Range pastRange) {
        model.setContentKnown();
        return false;
      }
    });
    return new SnapshotImpl(wcn[0], model);
  }

  public List<Revision> getAllArtifacts() {
    return getAllArtifacts(null);
  }

  public List<Revision> getAllArtifacts(@Nullable final Function<Collection<Revision>, Boolean> controller) {
    Threads.assertLongOperationsAllowed();
    final List<Revision> result = Collections15.arrayList();
    final List<Revision> readonly = controller != null ? Collections.unmodifiableList(result) : null;
    Detach detach = addListener(ThreadGate.STRAIGHT, myBasis.getPast(), new ArtifactListener.Adapter() {
      public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
        result.add(lastRevision);
        return controller == null || controller.invoke(readonly);
      }
    });
    detach.detach(); // to make sure
    return result;
  }

  public int count() {
    Pair<WCN, Integer> pair = countWithWCN();
    return pair.getSecond().intValue();
  }

  public Pair<WCN, Integer> countWithWCN() {
    Threads.assertLongOperationsAllowed();
    final int[] counter = {0};
    final boolean[] passed = {false};
    final WCN[] wcn = {null};
    addListener(ThreadGate.STRAIGHT, myBasis.getPast(), new ArtifactListener.Adapter() {
      public boolean onListeningAcknowledged(WCN.Range past, WCN.Range future) {
        wcn[0] = past.getEnd();
        return true;
      }

      public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
        counter[0]++;
        return true;
      }

      public boolean onPastPassed(WCN.Range pastRange) {
        passed[0] = true;
        return false;
      }
    });
    assert wcn[0] != null : this;
    assert passed[0] : this;
    return Pair.create(wcn[0], counter[0]);
  }

  public String toString() {
    return getClass().getName() + "[" + myRange + "," + myStrategy + "]";
  }

  public Revision queryLatest(Filter filter, RevisionAccess strategy) {
    return changeStrategy(strategy).queryLatest(filter);
  }

  public Revision queryLatest(final Filter filter) {
    Threads.assertLongOperationsAllowed();
    final boolean[] set = {false};
    final Revision[] result = {null};
    addListener(ThreadGate.STRAIGHT, WCN.intersect(myBasis.getPast(), myRange), new ArtifactListener.Adapter(false) {
      public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
        if (set[0])
          return false;
        if (filter.accept(lastRevision)) {
          result[0] = lastRevision;
          set[0] = true;
          return false;
        } else {
          return true;
        }
      }

      public boolean onPastPassed(WCN.Range pastRange) {
        if (!set[0]) {
          set[0] = true;
          result[0] = null;
        }
        return false;
      }
    });
    return result[0];
  }

  public Revision queryLatest() {
    return queryLatest(Filter.ALL);
  }

  public WCN.Range getRange() {
    return myRange;
  }

  public RevisionAccess getStrategy() {
    return myStrategy;
  }

  public ViewFilteringPolicy getFilteringPolicy() {
    return myFilteringPolicy;
  }

  public ViewFilteringPolicy getBackupFilteringPolicy() {
    return myBackupFilteringPolicy.get();
  }

  public Basis getBasis() {
    return myBasis;
  }

  protected boolean __debug(String message) {
    if (__debugName != null) {
      String name = "";
      StackTraceElement[] stackTrace = new Throwable().getStackTrace();
      if (stackTrace.length > 1) {
        name = stackTrace[1].getClassName();
        name = name.substring(name.lastIndexOf('.') + 1);
      }
      System.out.println("__(" + name + ") " + __debugName + ": " + message);
    }
    return true;
  }
}

