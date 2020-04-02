package com.almworks.database.objects.remote;

import com.almworks.api.database.*;
import com.almworks.database.Basis;
import com.almworks.database.objects.*;
import org.almworks.util.Util;

public class RCBMainChain extends AbstractRevisionChain {
  private final MyRevisionIterator myRevisionIterator = new MyRevisionIterator();

  private RevisionChain myMainChain = null;

  RCBMainChain(Basis basis, long atomKey, RCBPhysicalChains physicalChains) {
    super(basis, atomKey);
    setMainChain(physicalChains.getMainChain());
  }

  public synchronized Revision getFirstRevision() {
    assert myMainChain != null;
    return myBasis.getRevision(myBasis.getAtomID(myMainChain.getFirstRevision()), myRevisionIterator);
  }

//  public ScalarModel<Revision> getLastRevisionModel() {
//    return myLastRevisionModel;
//  }

  public RevisionIterator getRevisionIterator() {
    return myRevisionIterator;
  }

  synchronized void setMainChain(RevisionChain mainChain) {
    assert mainChain != null;
    assert mainChain instanceof PhysicalRevisionChain;
    if (Util.equals(mainChain, myMainChain))
      return;
//    myDetach.detach();
    myMainChain = mainChain;
//    ScalarModel<Revision> chainModel = myBasis.ourRevisionMonitor.getLastRevisionModel(myMainChain.getKey());
//    myDetach = chainModel.getEventSource().addStraightListener(new ScalarModel.Adapter<Revision>() {
//      public void onScalarChanged(ScalarModelEvent<Revision> event) {
//        synchronized (RCBMainChain.this) {
//          Revision newRevision = event.getNewValue();
//          Revision r = myBasis.getRevision((AtomKey) newRevision.getKey(), myRevisionIterator);
//          myLastRevision = r;
//        }
//      }
//    });
  }

  public Revision doGetLastRevision() {
    RevisionChain mainChain = myMainChain;
    if (mainChain == null)
      return null;
    long chainId = mainChain.getKey();
    long atomId = myBasis.ourRevisionMonitor.getOrCalculateLastRevisionAtomId(chainId);
    if (atomId > 0) {
      return myBasis.getRevision(atomId, myRevisionIterator);
    } else {
      assert false : myKey + " " + chainId + " " + atomId;
      return null;
    }
  }

  public synchronized boolean containsRevision(Revision revision) {
    RevisionChain mainChain = myMainChain;
    assert mainChain != null;
    if (mainChain == null)
      return false;
    return mainChain.containsRevision(revision);
  }

  public long getRevisionOrder(Revision revision) {
    // multiplying just to be compatible with revision order in RCBLocalChain
    return super.getRevisionOrder(revision) * 100 - 1;
  }


  private final class MyRevisionIterator extends PhysicalRevisionIterator {
    public RevisionAccess getStrategy() {
      return RevisionAccess.ACCESS_MAINCHAIN;
    }

    public RevisionChain getChain(Basis basis, long atomID) {
      return RCBMainChain.this;
    }
  }
}
