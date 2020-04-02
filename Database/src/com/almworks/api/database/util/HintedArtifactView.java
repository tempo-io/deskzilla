package com.almworks.api.database.util;

import com.almworks.api.database.*;
import com.almworks.database.AbstractArtifactView;
import com.almworks.database.objects.DBUtil;
import com.almworks.util.commons.Procedure;
import com.almworks.util.exec.ImmediateThreadGate;
import com.almworks.util.exec.ThreadGate;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.DetachComposite;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.Set;

public class HintedArtifactView extends AbstractArtifactView {
  private final long myHintUcn;
  private final ArtifactView myDelegate;

  private volatile Revision[] myHint;

  public HintedArtifactView(@NotNull Revision[] hint, long hintUcn, @NotNull ArtifactView delegate) {
    super(((AbstractArtifactView) delegate).getBasis(), WCN.ETERNITY, delegate.getStrategy());
    assert hintUcn <= myBasis.getUnderlyingUCN() : hintUcn + " " + myBasis.getUnderlyingUCN();
    myHint = hint;
    myHintUcn = hintUcn;
    myDelegate = delegate;
  }

  public Detach addListener(ThreadGate callbackGate, WCN.Range range, ArtifactListener listener) {
    Revision[] hint = myHint;
    WCN currentWCN = myBasis.getCurrentWCN();
    if (range.getStartUCN() != 0 || range.getEndUCN() < myHintUcn || hint == null) {
      // cannot handle partial requests, and when disposed
      return myDelegate.addListener(callbackGate, range, listener);
    }
    if (currentWCN.getUCN() != myHintUcn) {
      // outdated
      dispose();
      return myDelegate.addListener(callbackGate, range, listener);
    }
    WCN.Range future = range.getEndUCN() > myHintUcn ? WCN.createRange(myHintUcn, range.getEndUCN()) : WCN.NIL;
    DetachComposite lifespan = new DetachComposite();
    MyExecutor executor = new MyExecutor(lifespan, hint, myHintUcn, listener);
    if (!future.isNil()) {
      if (callbackGate instanceof ImmediateThreadGate) {
        assert false : callbackGate;
        Log.error("bad gate " + callbackGate + " for future " + future);
        return Detach.NOTHING;
      }
      lifespan.add(myDelegate.addListenerFuture(callbackGate, executor));
    }
    boolean proceed = listener.onListeningAcknowledged(WCN.createRange(0, myHintUcn), future);
    if (!proceed) {
      lifespan.detach();
      return Detach.NOTHING;
    }
    callbackGate.execute(executor);
    return lifespan;
  }

  public Filter getFilter() {
    return myDelegate.getFilter();
  }

  public Set<Revision> filter(Set<Revision> source) {
    return myDelegate.filter(source);
  }

  public Snapshot takeSnapshot() {
    return myDelegate.takeSnapshot();
  }

  public boolean isCountQuicklyAvailable() {
    return myDelegate.isCountQuicklyAvailable();
  }

  public boolean isVisible(Revision revision, boolean strictStrategy) {
    return myDelegate.isVisible(revision, strictStrategy);
  }

  public void verify(Procedure<Boolean> reportTo) {
    myDelegate.verify(reportTo);
  }

  public ArtifactView switchStrategy(RevisionAccess strategy) {
    return myDelegate.changeStrategy(strategy);
  }

  public void dispose() {
    myHint = null;
  }

  public boolean isDisposed() {
    return myHint == null;
  }


  private class MyExecutor implements Runnable, ArtifactListener {
    private final DetachComposite myDetach;
    private final long myHintUcn;
    private final ArtifactListener myListener;

    private Revision[] myHint;
    private boolean myFutureEnabled = false;
    private List<FutureEvent> myFutureEvents = null;

    public MyExecutor(DetachComposite lifespan, Revision[] hint, long hintUcn, ArtifactListener listener) {
      myDetach = lifespan;
      myHint = hint;
      myHintUcn = hintUcn;
      myListener = listener;
    }

    public void run() {
      Revision[] hint = myHint;
      myHint = null; // memory will be freed if any exception happens!

      if (myDetach.isEnded() || hint == null)
        return;
      int length = hint.length;
      for (int i = 0; i < length && !myDetach.isEnded(); i++) {
        Revision revision = hint[i];
        answer(myListener.onArtifactExists(revision.getArtifact(), revision));
      }
      if (myDetach.isEnded())
        return;
      answer(myListener.onPastPassed(WCN.createRange(0, myHintUcn)));

      while (true) {
        if (myDetach.isEnded())
          return;
        List<FutureEvent> fire = null;

        synchronized (this) {
          if (myFutureEvents == null) {
            myFutureEnabled = true;
            break;
          }
          fire = myFutureEvents;
          myFutureEvents = null;
        }

        // while we are firing postponed events, more events may come - thus the cycle
        for (FutureEvent event : fire) {
          try {
            if (!answer(event.call(myListener)))
              break;
          } catch (Exception e) {
            Log.error(e);
          }
        }
      }
    }

    public boolean onListeningAcknowledged(WCN.Range past, WCN.Range future) {
      return !myDetach.isEnded();
    }

    public boolean onPastPassed(WCN.Range pastRange) {
      return !myDetach.isEnded();
    }

    public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
      return !myDetach.isEnded();
    }

    public boolean onWCNPassed(WCN wcn) {
      return fireOrPostpone(new FutureWCN(wcn));
    }

    public boolean onArtifactAppears(Artifact artifact, Revision lastRevision) {
      WCN wcn = lastRevision.getWCN();
      if (wcn.getUCN() < myHintUcn)
        return myDetach.isEnded();
      Revision prevRevision = DBUtil.getPrevRevisionForView(lastRevision);
      if (prevRevision != null && prevRevision.getWCN().getUCN() < myHintUcn && myDelegate.isVisible(prevRevision, true)) {
        return fireOrPostpone(new FutureChanges(artifact, prevRevision, lastRevision));
      } else {
        return fireOrPostpone(new FutureAppears(artifact, lastRevision));
      }
    }

    public boolean onArtifactDisappears(Artifact artifact, Revision lastSeenRevision, Revision unseenRevision) {
      return fireOrPostpone(new FutureDisappears(artifact, lastSeenRevision, unseenRevision));
    }

    public boolean onArtifactChanges(Artifact artifact, Revision prevRevision, Revision newRevision) {
      return fireOrPostpone(new FutureChanges(artifact, prevRevision, newRevision));
    }

    private boolean fireOrPostpone(FutureEvent event) {
      boolean fire;
      synchronized(this) {
        fire = myFutureEnabled;
        if (!fire) {
          if (myFutureEvents == null)
            myFutureEvents = Collections15.arrayList();
          myFutureEvents.add(event);
        }
      }
      if (fire) {
        return answer(event.call(myListener));
      } else {
        return !myDetach.isEnded();
      }
    }

    private boolean answer(boolean listenerAnswer) {
      if (!listenerAnswer) {
        myDetach.detach();
      }
      return listenerAnswer;
    }
  }


  private static abstract class FutureEvent {
    public abstract boolean call(ArtifactListener listener);
  }


  private static class FutureAppears extends FutureEvent {
    private final Artifact myArtifact;
    private final Revision myLastRevision;

    public FutureAppears(Artifact artifact, Revision lastRevision) {
      myArtifact = artifact;
      myLastRevision = lastRevision;
    }

    public boolean call(ArtifactListener listener) {
      return listener.onArtifactAppears(myArtifact, myLastRevision);
    }
  }


  private static class FutureDisappears extends FutureEvent {
    private final Artifact myArtifact;
    private final Revision myLastSeenRevision;
    private final Revision myUnseenRevision;

    public FutureDisappears(Artifact artifact, Revision lastSeenRevision, Revision unseenRevision) {
      myArtifact = artifact;
      myLastSeenRevision = lastSeenRevision;
      myUnseenRevision = unseenRevision;
    }

    public boolean call(ArtifactListener listener) {
      return listener.onArtifactDisappears(myArtifact, myLastSeenRevision, myUnseenRevision);
    }
  }


  private static class FutureChanges extends FutureEvent {
    private final Artifact myArtifact;
    private final Revision myPrevRevision;
    private final Revision myNewRevision;

    public FutureChanges(Artifact artifact, Revision prevRevision, Revision newRevision) {
      myArtifact = artifact;
      myPrevRevision = prevRevision;
      myNewRevision = newRevision;
    }

    public boolean call(ArtifactListener listener) {
      return listener.onArtifactChanges(myArtifact, myPrevRevision, myNewRevision);
    }
  }


  private static class FutureWCN extends FutureEvent {
    private final WCN myWCN;

    public FutureWCN(WCN WCN) {
      myWCN = WCN;
    }

    public boolean call(ArtifactListener listener) {
      return listener.onWCNPassed(myWCN);
    }
  }
}

