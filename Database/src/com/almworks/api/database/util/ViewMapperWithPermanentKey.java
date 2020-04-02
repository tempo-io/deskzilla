package com.almworks.api.database.util;

import com.almworks.api.database.*;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.CanBlock;
import javolution.util.SimplifiedFastMap;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

/**
 * The same view mapper but more error-proof and that takes advantage of the fact that once an
 * artifact gets a key (non-null), it will never change.
 */

public class ViewMapperWithPermanentKey<T> extends ViewMapper<T> {
  private Workspace myWorkspace;
  private volatile int myRequestCountAheadOfTransaction;

  public ViewMapperWithPermanentKey(ArtifactView view, ArtifactPointer attribute, Class<T> valueClass) {
    super(view, attribute, valueClass, new SimplifiedFastMap<T, Revision>());
  }

  public static <T> ViewMapperWithPermanentKey<T> create(ArtifactView view, ArtifactPointer attribute,
    Class<T> valueClass)
  {
    return new ViewMapperWithPermanentKey<T>(view, attribute, valueClass);
  }

  @Nullable
  public Artifact getRevisionNow(T key) {
    if (key == null)
      return null;
    // first - unsynchronized
    Artifact artifact = mapGet(key);
    if (artifact != null)
      return artifact;
    // check if anything changed but not yet processed
    long sync = getSyncUcn();
    long current = getCurrentUcn();
    if (sync < current) {
      // cannot search through view - mapper has not yet found
      Log.warn("ViewMapper is behind time");
      assert false : this + " " + key + " " + sync + " " + current;
    }
    return null;
  }

  private long getCurrentUcn() {
    if (myWorkspace == null)
      myWorkspace = Context.require(Workspace.class);
    return myWorkspace.getCurrentWCN().getUCN() - 1;
  }


//private static final DebugDichotomy _dd = new DebugDichotomy("w+", "w-", 50).dump(2500);
//private static final DebugMeanCounter _dm = new DebugMeanCounter("ucnDiff").dump(2500);

  @CanBlock
  @Nullable
  public Artifact getRevisionLong(final T key) {
    if (key == null) {
//      _dd.a();
      return null;
    }
    // first - unsynchronized
    Artifact artifact = mapGet(key);
    if (artifact != null) {
//      _dd.a();
      return artifact;
    }
    // check if anything changed but not yet processed
    long current = getCurrentUcn();
    final long sync[] = {getSyncUcn()};
    if (sync[0] >= current) {
      // we're in sync - there's no artifact
//      _dd.a();
      return null;
    }

//    _dm.add(current - sync[0]);
//    _dd.b();
    myRequestCountAheadOfTransaction++;
    // look through artifacts which our listener has not yet catched
    final Artifact[] result = {null};
    getView().addListener(ThreadGate.STRAIGHT, WCN.createRange(WCN.createWCN(sync[0]), WCN.LATEST),
      new ArtifactListener.Adapter() {
        public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
          T artifactKey = getKey(lastRevision);
          if (artifactKey != null && artifactKey.equals(key)) {
            result[0] = artifact;
            return false;
          }
          long newSync = getSyncUcn();
          if (newSync > sync[0]) {
            sync[0] = newSync;
            result[0] = mapGet(key);
            if (result[0] != null) {
              return false;
            }
          }
          return true;
        }

        public boolean onPastPassed(WCN.Range pastRange) {
          return false;
        }
      });
    return result[0];
  }

  public String toString() {
    return "VMWPK[" + getView() + "; " + getAttribute() + "]";
  }

  public int getRequestCountAheadOfTransaction() {
    return myRequestCountAheadOfTransaction;
  }
}
