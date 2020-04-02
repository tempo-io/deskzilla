package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.database.bitmap.BitmapRootArtifactView;
import com.almworks.database.filter.FilterManagerImpl;
import com.almworks.database.objects.DBUtil;
import com.almworks.util.commons.Condition;
import com.almworks.util.commons.Procedure;
import com.almworks.util.exec.ThreadGate;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.DetachComposite;

import java.util.List;
import java.util.Set;

/**
 * :todoc:
 *
 * @author sereda
 */
public class FilteredArtifactView extends AbstractArtifactView {
  public List<ArtifactListener> __debugListeners;
  protected final Filter myFilter;

  protected final ArtifactView myParent;

  public FilteredArtifactView(Basis basis, ArtifactView parent, Filter filter, WCN.Range range) {
    super(basis, WCN.intersect(parent.getRange(), range == null ? WCN.ETERNITY : range), parent.getStrategy());
    assert filter != null;
    myParent = parent;
    myFilter = filter;
  }

  public Detach addListener(ThreadGate callbackGate, WCN.Range range, final ArtifactListener listener) {
    final WCN.Range listenedRange = WCN.intersect(myRange, range);
    if (listenedRange.isNil())
      return Detach.NOTHING;

    __debugAddListener(listener);

    final ArtifactListener filteringListener = getFilteringListener(listener, listenedRange.getEnd(), listenedRange.getStart());
    Detach detach = myParent.addListener(callbackGate, listenedRange, filteringListener);

    if (__debugView) {
      detach = DetachComposite.create(detach, new Detach() {
        protected void doDetach() {
          ((FilteringListener) filteringListener).__debugWrap(false);
        }
      });
    }

    return detach;
  }

  public Filter getFilter() {
    return myFilter;
  }

  public void verify(Procedure<Boolean> reportTo) {
    myParent.verify(reportTo);
  }

  public boolean isVisible(Revision revision, boolean strictStrategy) {
    if (strictStrategy) {
      Artifact artifact = revision.getArtifact();
      if (!artifact.isAccessStrategySupported(myStrategy))
        return false;
      RevisionChain chain = artifact.getChain(myStrategy);
      if (!chain.equals(revision.getChain())) {
        revision = chain.getRevisionOnChainOrNull(revision);
        if (revision == null)
          return false;
      }
    }
    if (!myFilter.accept(revision))
      return false;
    // do not transfer strictStrategy because we already converted revision to our chain
    return myParent.isVisible(revision, false);
  }

  public Set<Revision> filter(Set<Revision> source) {
    return new Condition<Revision>() {
      public boolean isAccepted(Revision value) {
        return myFilter.accept(value);
      }
    }.filterSet(source);
  }

  public boolean isCountQuicklyAvailable() {
    return false;
  }

  public String getDebugFilterString() {
    String parentDebug = myParent instanceof FilteredArtifactView ?
      ((FilteredArtifactView) myParent).getDebugFilterString() : myParent.toString();
    return "[" + myFilter.toString() + "] AND " + parentDebug;
  }

  public ArtifactView switchStrategy(RevisionAccess strategy) {
    assert myStrategy != strategy;
    assert myParent.getStrategy().equals(myStrategy);
    ArtifactView parent = myParent.changeStrategy(strategy);
    assert parent.getStrategy().equals(strategy);
    return new FilteredArtifactView(myBasis, parent, myFilter, myRange);
  }

  public String toString() {
    return super.toString() + "[" + myFilter + "] P[" + myParent + "]";
  }

  protected void __debugAddListener(final ArtifactListener listener) {
    if (!__debugView)
      return;
    synchronized (this) {
      if (__debugListeners == null)
        __debugListeners = Collections15.arrayList();
      __debugListeners.add(listener);
    }
  }

  protected boolean accept(Revision object) {
    return object != null && myFilter.accept(object);
  }

  protected ArtifactListener getFilteringListener(final ArtifactListener listener, final WCN end, WCN start) {
    return new FilteringListener(listener, end, start);
  }

  private void __debugRemoveListener(ArtifactListener listener) {
    if (!__debugView)
      return;
    synchronized (this) {
      if (__debugListeners == null)
        return;
      __debugListeners.remove(listener);
    }
  }

  protected ArtifactView getPlainFilteredView() {
    ArtifactView parent = myParent;
    if (parent instanceof FilteredArtifactView) {
      parent = ((FilteredArtifactView) parent).getPlainFilteredView();
    } else if (parent instanceof BitmapRootArtifactView) {
      SystemViewsImpl tempViewProvider = new SystemViewsImpl(myBasis, new FilterManagerImpl(myBasis, myBasis));
      parent = new ScanningRootArtifactView(myBasis, tempViewProvider, myStrategy);
    }
    return new FilteredArtifactView(myBasis, parent, myFilter, myRange);
  }


  protected class FilteringListener implements ArtifactListener {
    protected final WCN myEnd;
    protected final WCN myStart;
    protected final ArtifactListener myListener;

    public FilteringListener(ArtifactListener listener, WCN end, WCN start) {
      myListener = listener;
      myEnd = end;
      myStart = start;
    }

    public boolean onListeningAcknowledged(WCN.Range past, WCN.Range future) {
      return __debugWrap(myListener.onListeningAcknowledged(past, future));
    }

    public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
      WCN wcn = lastRevision.getWCN();
      if (wcn.isEarlier(myEnd) && myStart.isEarlierOrEqual(wcn)) {
        if (accept(lastRevision)) {
          return __debugWrap(myListener.onArtifactExists(artifact, lastRevision));
        }
      }
      return true;
    }

    public boolean onPastPassed(WCN.Range pastRange) {
      return __debugWrap(myListener.onPastPassed(pastRange));
    }

    public boolean onArtifactAppears(Artifact artifact, Revision lastRevision) {
      if (lastRevision.getWCN().isEarlier(myEnd)) {
        if (accept(lastRevision)) {
          if (myStart.getUCN() > 0) {
            Revision prevRevision = DBUtil.getPrevRevisionForView(lastRevision);
            if (prevRevision != null && prevRevision.getWCN().isEarlierOrEqual(myStart) && isVisible(prevRevision, false)) {
              return __debugWrap(myListener.onArtifactChanges(artifact, prevRevision, lastRevision));
            }
          }
          return __debugWrap(myListener.onArtifactAppears(artifact, lastRevision));
        } else {
          // this a special case when we're listening the future. RootArtifactView
          // does not scan the past and does not know if there was previous artifact.
          // so it may report onArtifactAppears.
          //
          // The trick is, that the new revision may not satisfy the filter,
          // even though PREVIOUS revision of this artifact did satisfy the filter.
          // That may lead to a problem when, for example, artifact is deleted, but
          // its filter that listens "the future" does not access any update.
          //
          // So we here do an extra check.
          //
          Revision prevRevision = DBUtil.getPrevRevisionForView(lastRevision);
          if (prevRevision != null && isVisible(prevRevision, false)) {
            //boolean b1 = listener.onArtifactAppears(artifact, prevRevision);
            return __debugWrap(myListener.onArtifactDisappears(artifact, prevRevision, lastRevision));
          }
        }
      }
      return true;
    }

    public boolean onArtifactDisappears(Artifact artifact, Revision lastSeenRevision, Revision unseenRevision) {
      if (unseenRevision.getWCN().isEarlier(myEnd)) {
        if (accept(lastSeenRevision))
          return __debugWrap(myListener.onArtifactDisappears(artifact, lastSeenRevision, unseenRevision));
      }
      return true;
    }

    public boolean onArtifactChanges(final Artifact artifact, Revision prevRevision, final Revision newRevision) {
      if (newRevision.getWCN().isEarlier(myEnd)) {
        boolean acceptNow = accept(newRevision);
        boolean acceptBefore = accept(prevRevision);
        if (acceptBefore) {
          if (acceptNow)
            return __debugWrap(myListener.onArtifactChanges(artifact, prevRevision, newRevision));
          else
            return __debugWrap(myListener.onArtifactDisappears(artifact, prevRevision, newRevision));
        } else {
          if (acceptNow)
            return __debugWrap(myListener.onArtifactAppears(artifact, newRevision));
        }
      }
      return true;
    }

    public boolean onWCNPassed(WCN wcn) {
      if (wcn.isEarlierOrEqual(myEnd)) {
        return __debugWrap(myListener.onWCNPassed(wcn));
      } else {
        return __debugWrap(false);
      }
    }

    public String toString() {
      return __debugView ? "((" + myFilter + "))     ===>    " + myListener : super.toString();
    }

    protected boolean __debugWrap(boolean listenerCallResult) {
      if (__debugView && !listenerCallResult) {
        __debugRemoveListener(myListener);
      }
      return listenerCallResult;
    }
  }
}
