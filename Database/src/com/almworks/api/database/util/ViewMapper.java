package com.almworks.api.database.util;

import com.almworks.api.database.*;
import com.almworks.util.commons.Condition;
import com.almworks.util.exec.ThreadGate;
import javolution.util.SimplifiedFastMap;
import org.almworks.util.Log;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.DetachComposite;
import org.jetbrains.annotations.*;
import util.concurrent.*;

import java.util.Map;

/**
 * This class tracks given view and maintains a map of all artifacts that have a given attribute
 */
public class ViewMapper<T> {
  private final ArtifactView myView;
  private final ArtifactPointer myAttribute;
  private final Class<T> myValueClass;

  private final Object myLock = new Object();
  private final Map<T, Artifact> myMap;
  private final SynchronizedBoolean myStarted = new SynchronizedBoolean(false);
  private final SynchronizedInt myInitialized = new SynchronizedInt(0);
  private final Synchronized<WCN> mySyncWCN = new Synchronized<WCN>(WCN.EARLIEST);

  private final DetachComposite myDetach = new DetachComposite(true);
  private final MyListener myListener = new MyListener();

  public ViewMapper(ArtifactView view, ArtifactPointer attribute, Class<T> valueClass) {
    this(view, attribute, valueClass, new SimplifiedFastMap());
  }

  protected ViewMapper(@NotNull ArtifactView view, @NotNull ArtifactPointer attribute, @NotNull Class<T> valueClass,
    @NotNull Map mapInstance)
  {
    myView = view;
    myAttribute = attribute;
    myValueClass = valueClass;
    myMap = mapInstance;
  }

  public static <T> ViewMapper<T> create(ArtifactView view, ArtifactPointer attribute, Class<T> valueClass) {
    return new ViewMapper<T>(view, attribute, valueClass);
  }

  public Detach start() {
    if (!myStarted.commit(false, true))
      return Detach.NOTHING;
//    ThreadGate startGate = ThreadGate.STRAIGHT;
    final ThreadGate gate = ThreadGate.LONG(ViewMapper.class);
    gate.execute(new Runnable() {
      public void run() {
        myDetach.add(myView.addListener(ThreadGate.STRAIGHT, WCN.ETERNITY, myListener));
        myInitialized.increment();
      }
    });
    return myDetach;
  }

  protected Artifact mapGet(T key) {
    synchronized (myLock) {
      return myMap.get(key);
    }
  }

  protected long getSyncUcn() {
    WCN wcn = mySyncWCN.get();
    return wcn == null ? 0 : wcn.getUCN();
  }

  public ArtifactView getView() {
    return myView;
  }

  public ArtifactPointer getAttribute() {
    return myAttribute;
  }

  public void waitInitialized() throws InterruptedException {
    myInitialized.waitForValue(2, 0);
  }

  public void waitWCN(final WCN wait) throws InterruptedException {
    waitInitialized();
    mySyncWCN.waitForCondition(new Condition<WCN>() {
      public boolean isAccepted(WCN real) {
        return real.getUCN() >= wait.getUCN();
      }
    });
  }

  @Nullable
  public Artifact getRevisionNow(T key) {
    if (key == null)
      return null;
    synchronized (myLock) {
      return myMap.get(key);
    }
  }

  private boolean update(Revision remove, Revision add) {
    T removeKey = remove != null ? getKey(remove) : null;
//    Debug.__out("remove " + remove + "; key " + removeKey);
    T addKey = add != null ? getKey(add) : null;
//    Debug.__out("add " + add + "; key " + addKey);
    synchronized (myLock) {
      if (removeKey != null) {
        Artifact removed = myMap.remove(removeKey);
        if (!WorkspaceUtils.equals(removed, remove.getArtifact())) {
          Log.warn(this + ": bad remove " + remove.getArtifact() + " " + removed);
          myMap.put(removeKey, removed);
        }
      }
      if (addKey != null) {
        Artifact newArtifact = add.getArtifact();
        Artifact expunged = myMap.put(addKey, newArtifact);
        if (expunged != null) {
          if (!WorkspaceUtils.equals(expunged, newArtifact)) {
            boolean oldDeleted = expunged.getLastRevision().isDeleted();
            boolean newDeleted = newArtifact.getLastRevision().isDeleted();
            boolean putBack = !oldDeleted && (newDeleted || expunged.getKey() > newArtifact.getKey());
            if (putBack) {
              myMap.put(addKey, expunged);
              Log.warn(this + ": " + newArtifact + "(deleted:" + newDeleted + ") tried to expunge " + expunged + "(deleted:" + oldDeleted + ")");
            } else {
              Log.warn(this + ": " + newArtifact + " expunged " + expunged);
            }
          }
        }
      }
    }
    return true;
  }

  protected T getKey(Revision revision) {
    return revision.getValue(myAttribute, myValueClass);
  }

  public boolean isStarted() {
    return myStarted.get();
  }

  public void stop() {
    myDetach.detach();
  }

  private class MyListener extends ArtifactListener.Adapter {
    public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
      return update(null, lastRevision);
    }

    public boolean onPastPassed(WCN.Range pastRange) {
      mySyncWCN.set(pastRange.getEnd());
      myInitialized.increment();
      return true;
    }

    public boolean onArtifactAppears(Artifact artifact, Revision lastRevision) {
      return update(null, lastRevision);
    }

    public boolean onArtifactDisappears(Artifact artifact, Revision lastSeenRevision, Revision unseenRevision) {
      return update(lastSeenRevision, null);
    }

    public boolean onArtifactChanges(Artifact artifact, Revision prevRevision, Revision newRevision) {
      return update(prevRevision, newRevision);
    }

    public boolean onWCNPassed(WCN wcn) {
      mySyncWCN.set(wcn);
      return true;
    }
  }
}
