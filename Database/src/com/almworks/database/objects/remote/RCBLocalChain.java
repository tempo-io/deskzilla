package com.almworks.database.objects.remote;

import com.almworks.api.database.*;
import com.almworks.api.universe.*;
import com.almworks.database.Basis;
import com.almworks.database.objects.*;
import com.almworks.database.schema.Schema;
import org.almworks.util.Log;
import org.almworks.util.Util;

import java.util.Iterator;
import java.util.List;

public class RCBLocalChain extends AbstractRevisionChain {
  private final RCBHelperInterface myHelper;

  private MyRevisionIterator myIterator = null;
  private RCBPhysicalChains myPhysicalChains = null;

  RCBLocalChain(Basis basis, long atomKey, RCBPhysicalChains physicalChains, RCBHelperInterface helper)
  {
    super(basis, atomKey);
    myHelper = helper;
    setPhysicalChains(physicalChains);
  }

  public Revision getFirstRevision() {
    myHelper.rescan();
    assert myPhysicalChains != null;
    assert myPhysicalChains.getMainChain() != null;
    Revision result = myPhysicalChains.getMainChain().getFirstRevision();
    long mainFirstAtomID = myBasis.getAtomID(result);
    RevisionChain firstLocalChain = myPhysicalChains.getFirstLocalChain();
    if (firstLocalChain != null) {
      Atom firstLocalAtom = DBUtil.getInternals(firstLocalChain.getFirstRevision()).getAtom();
      long startLinkAtomID = RCBUtil.getOpenLink(myBasis, firstLocalChain, myHelper);
      long copiedFromAtomID = firstLocalAtom.getLong(Schema.KA_COPIED_FROM);
      if (startLinkAtomID == mainFirstAtomID && copiedFromAtomID == mainFirstAtomID) {
        result = firstLocalChain.getFirstRevision();
      }
    }
    return myBasis.getRevision(result.getKey(), getRevisionIterator());
  }

//  public ScalarModel<Revision> getLastRevisionModel() {
//    return myLastRevisionModel;
//  }

  public synchronized RevisionIterator getRevisionIterator() {
    if (myIterator == null)
      myIterator = new MyRevisionIterator();
    return myIterator;
  }

  void setPhysicalChains(RCBPhysicalChains physicalChains) {
    synchronized (myHelper.getArtifactLock()) {
      synchronized (this) {
        if (myPhysicalChains == physicalChains)
          return;
        myPhysicalChains = physicalChains;
        myIterator = null;
//        myDetach.detach();
//        myDetach = Detach.NOTHING;
//        RevisionChain lastLocalChain = myPhysicalChains.getLastLocalChain();
//        if (lastLocalChain == null)
//          watchMainChain();
//        else
//          watchLocalChain(lastLocalChain);
      }
    }
  }

//  private void watchLocalChain(final RevisionChain localChain) {
//    ScalarModel<Revision> chainModel = myBasis.ourRevisionMonitor.getLastRevisionModel(localChain.getKey());
//    myDetach = Detach.NOTHING;
//    myListeningMainChain = false;
//    Detach detach = chainModel.getEventSource().addStraightListener(new ScalarModel.Adapter<Revision>() {
//      public void onScalarChanged(ScalarModelEvent<Revision> event) {
//        Revision newRevision = event.getNewValue();
//        RevisionWithInternals r = myBasis.getRevision((AtomKey) newRevision.getKey(), getRevisionIterator());
//        if (RCBUtil.isClosed(myBasis, localChain, myHelper)) {
//          synchronized (myArtifactLock) {
//            synchronized (RCBLocalChain.this) {
//              switchToMainChain();
//            }
//          }
//        } else {
//          myLastRevision = r;
//        }
//      }
//    });
//    if (myListeningMainChain) {
//      detach.detach();
//    } else {
//      myDetach = detach;
//    }
//  }

//  void switchToMainChain() {
//    synchronized (myArtifactLock) {
//      synchronized (this) {
//        if (!myListeningMainChain) {
//          myDetach.detach();
//          watchMainChain();
//        }
//      }
//    }
//  }

//  private void watchMainChain() {
//    RevisionChain mainChain = myPhysicalChains.getMainChain();
//    if (mainChain == null) {
//      assert false : myKey;
//      myDetach = Detach.NOTHING;
//      myListeningMainChain = true;
//      return;
//    }
//    myListeningMainChain = true;
//    ScalarModel<Revision> chainModel = myBasis.ourRevisionMonitor.getLastRevisionModel(mainChain.getKey());
//    myDetach = chainModel.getEventSource().addStraightListener(new ScalarModel.Adapter<Revision>() {
//      public void onScalarChanged(ScalarModelEvent<Revision> event) {
//        synchronized (myArtifactLock) {
//          synchronized (RCBLocalChain.this) {
//            Revision newRevision = event.getNewValue();
//            myLastRevision = myBasis.getRevision((AtomKey) newRevision.getKey(), getRevisionIterator());
//          }
//        }
//      }
//    });
//  }

  public Revision doGetLastRevision() {
    assert!Thread.holdsLock(this) || Thread.holdsLock(myHelper.getArtifactLock()) : "deadlock possible";  // lock on chain is only possible if lock on artifact is held
    synchronized (myHelper.getArtifactLock()) {
      synchronized (this) {
        myHelper.rescan();
        RCBPhysicalChains chains = myPhysicalChains;
        if (chains == null)
          return null;
        RevisionChain localChain = chains.getLastLocalChain();
        long chainId;
        if (localChain != null && !RCBUtil.isClosed(myBasis, localChain, myHelper)) {
          chainId = localChain.getKey();
        } else {
          RevisionChain mainChain = chains.getMainChain();
          if (mainChain == null) {
            assert false : myKey;
            return null;
          }
          chainId = mainChain.getKey();
        }
        long atomId = myBasis.ourRevisionMonitor.getOrCalculateLastRevisionAtomId(chainId);
        if (atomId > 0) {
          return myBasis.getRevision(atomId, getRevisionIterator());
        } else {
          assert false : chainId + " " + atomId + " " + myKey;
          return null;
        }
      }
    }
  }

  public boolean containsRevision(Revision revision) {
    synchronized (myHelper.getArtifactLock()) {
      synchronized (this) {
        myHelper.rescan();
        List<RevisionChain> chains = myPhysicalChains.getLocalChains();
        if (chains.size() == 0) {
          RevisionChain mainChain = myPhysicalChains.getMainChain();
          assert mainChain != null;
          if (mainChain == null)
            return false;
          return mainChain.containsRevision(revision);
        } else {
          for (int i = 0; i < chains.size(); i++) {
            RevisionChain chain = chains.get(i);
            if (chain.containsRevision(revision))
              return true;
          }
          // fall back to going through iterator.
          return super.containsRevision(revision);
        }
      }
    }
  }

  public long getRevisionOrder(Revision revision) {
    // the idea is to multiply standard wcn-based revision order by 100 to address
    // inter-wcn variations. we also track main chain revisions separately, because they
    // could be created out-of-order because of chain closure.
    synchronized (myHelper.getArtifactLock()) {
      synchronized (this) {
        myHelper.rescan();
        Atom atom = DBUtil.getInternals(revision).getAtom();
        RevisionChain chain = DBUtil.getPhysicalChain(myBasis, atom);
        assert chain != null : revision;
        if (chain.equals(myPhysicalChains.getMainChain())) {
          // this is main chain revision - do extra to ensure that we don't access decreasing order
          long order = super.getRevisionOrder(revision) * 100;
          RevisionWithInternals previous = getRevisionIterator().getPreviousRevision(myBasis, atom);
          if (previous != null)
            order = Math.max(order, getRevisionOrder(previous) + 1);
          return order;
        } else {
          // this is local chain revision. if there's COPIED_FROM junction, then this is a start of
          // local revision, and we need to make a leverage for this atom (inc 0). if it is ordinary
          // local chain revision, we add inc 10 to make sure they are after COPIED_FROM atoms.
          long copiedFrom = Schema.KA_COPIED_FROM.get(atom);
          int inc = copiedFrom == -1 ? 10 : 0;
          List<RevisionChain> chains = myPhysicalChains.getLocalChains();
          for (int i = 0; i < chains.size(); i++) {
            if (chain.equals(chains.get(i)))
              return super.getRevisionOrder(revision) * 100 + inc;
          }
        }
        // buried chains and chains of other artifacts don't count
        return -1;
      }
    }
  }

  private final class MyRevisionIterator extends RevisionIterator {
//    private final Throwable myCreationStack = new Throwable();

    public RevisionWithInternals getPreviousRevision(Basis basis, Atom atom) {
      myHelper.rescan();
      synchronized (myHelper.getArtifactLock()) {
        synchronized (RCBLocalChain.this) {
          RCBPhysicalChains chains = myPhysicalChains;
          RevisionChain chain = DBUtil.getPhysicalChain(basis, atom);
          if (isMainChain(chains, chain)) {
            RevisionWithInternals revision = basis.getRevision(atom.getAtomID(), this);
            return getMainChainRevisionPrev(chains, revision, 0);
          } else {
            return getPrevFromLocalChain(basis, chains, atom, chain);
          }
        }
      }
    }

    public RevisionAccess getStrategy() {
      return RevisionAccess.ACCESS_LOCAL;
    }

    public RevisionChain getChain(Basis basis, long atomID) {
      return RCBLocalChain.this;
    }

    private int findLocalChain(RCBPhysicalChains chains, RevisionChain chain) {
      int count = 0;
      for (RevisionChain rc : chains.getLocalChains()) {
        if (Util.equals(rc, chain))
          return count;
        count++;
      }
      return -1;
    }

    private RevisionWithInternals getMainChainRevisionPrev(RCBPhysicalChains chains,
      RevisionWithInternals mainChainRevision, int skip)
    {
      boolean onMainChain = DBUtil.getPhysicalChain(myBasis, mainChainRevision.getAtom()).equals(chains.getMainChain());
      if (!onMainChain) {
        assert false: myKey + " " + mainChainRevision.getKey() + " " + chains.getMainChain();
        return null;
      }
      // todo [important] possible corruption if reincarnation is crashed

      long atomID = mainChainRevision.getAtom().getAtomID();
      Index index = myBasis.getIndex(Schema.INDEX_RCB_ENDLINK_MC);
      Iterator<Atom> ii = index.search(Particle.createLong(atomID));
      int count = 0;
      while (ii.hasNext()) {
        Atom atom = ii.next();
        if (atom.getLong(Schema.KA_RCB_LINK_ENDLOCALBRANCH) != atomID)
          break;
        if (count++ < skip)
          continue;
        long localChainID = atom.getLong(Schema.KA_RCB_LINK_LOCALCHAIN);
        RevisionChain localChain = myBasis.getPhysicalChain(localChainID);
        assert chains.getLocalChains().contains(localChain);
        RevisionWithInternals result = getRevisionWithInternals(localChain.getLastRevision());
        if (!DBUtil.hasUserContent(result.getAtom()))
          result = getPrevFromLocalChain(myBasis, chains, result.getAtom(), localChain);
        return result;
      }
      return getRevisionWithInternals(getPhysicallyPreviousRevision(myBasis, mainChainRevision.getAtom()));
    }


    private RevisionWithInternals getPrevFromLocalChain(Basis basis, RCBPhysicalChains chains, Atom atom,
      RevisionChain chain)
    {

      int index = findLocalChain(chains, chain);
      if (index < 0) {
        if (chains.getBuriedChains().contains(chain)) {
          // we are iterating over reincarnated chain
          // todo seek which local chain corresponded to this atom in buried chain.
          Log.debug("iterating through buried chain");
          return null;
        } else {
          Log.warn("iterator cannot find local chain " + chain + " [atom " + atom + "], cs:", new Throwable());
          return null;
        }
      }
      RevisionWithInternals prev = getPhysicallyPreviousRevision(basis, atom);
      if (prev != null)
        return prev;
      // we're at the beginning of a chain
      long refAtomID = RCBUtil.getOpenLink(basis, chain, myHelper);
      if (refAtomID == -1) {
        Log.warn("iterator cannot find local chain open link " + chain + " [atom " + atom + "], no cs");
//        Log.warn("iterator cannot find local chain open link " + chain + " [atom " + atom + "], cs:", myCreationStack);
        return null;
      }
      RevisionWithInternals mainChainRevision = myBasis.getRevision(refAtomID, this);
      return getMainChainRevisionPrev(chains, mainChainRevision, index + 1);
    }

    private RevisionWithInternals getRevisionWithInternals(Revision revision) {
      if (revision == null)
        return null;
      return myBasis.getRevision(revision.getKey(), this);
    }

    private boolean isMainChain(RCBPhysicalChains chains, RevisionChain chain) {
      return Util.equals(chain, chains.getMainChain());
    }
  }
}



