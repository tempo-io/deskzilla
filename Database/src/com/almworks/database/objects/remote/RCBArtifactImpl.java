package com.almworks.database.objects.remote;

import com.almworks.api.database.*;
import com.almworks.api.database.util.DatabaseRunnable;
import com.almworks.api.database.util.WorkspaceUtils;
import com.almworks.api.universe.*;
import com.almworks.database.*;
import com.almworks.database.objects.*;
import com.almworks.database.schema.Schema;
import com.almworks.util.collections.Containers;
import com.almworks.util.collections.MapIterator;
import com.almworks.util.commons.Lazy;
import com.almworks.util.commons.Procedure2;
import com.almworks.util.threads.Threads;
import org.almworks.util.*;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class RCBArtifactImpl extends AbstractArtifactImpl implements RCBArtifact, RCBHelperInterface {
  private final Lazy<RCBLocalChain> myLocalAccessChain = new Lazy<RCBLocalChain>() {
    public RCBLocalChain instantiate() {
      return new RCBLocalChain(myBasis, myKey, myChains, RCBArtifactImpl.this);
    }
  };
  private final Lazy<RCBMainChain> myMainAccessChain = new Lazy<RCBMainChain>() {
    public RCBMainChain instantiate() {
      return new RCBMainChain(myBasis, myKey, myChains);
    }
  };
  private Map<Long, Relink> myReincarnationRelinks = null;
  private RCBPhysicalChains myChains = null;
  private RevisionCreator myReincarnation = null;
  private boolean myRescanNeeded = true;

  public RCBArtifactImpl(Basis basis, long atomKey) {
    super(basis, atomKey);
  }

  public Object getArtifactLock() {
    return this;
  }

  public static RevisionCreator change(Basis basis, TransactionExt transaction, Artifact artifact,
    RevisionAccess strategy, Revision baseRevision) throws DatabaseInconsistentException
  {
    if (strategy == RevisionAccess.ACCESS_DEFAULT)
      strategy = RevisionAccess.ACCESS_LOCAL;
    if (strategy == RevisionAccess.ACCESS_MAINCHAIN)
      return RCBArtifactLogic.changeMainChain(basis, transaction, artifact, baseRevision);
    else if (strategy == RevisionAccess.ACCESS_LOCAL) {
      RevisionCreationContextBean bean = new RevisionCreationContextBean();
      bean.setArtifact(artifact);
      bean.setRevisionChain(artifact.getChain(strategy));
      RCBArtifactImpl impl = getImpl(artifact);
      RevisionCreator creator = impl.changeLocal(transaction, bean, baseRevision);
      bean.setRevisionCreator(creator);
      return creator;
    } else {
      throw new IllegalArgumentException("cannot understand strategy " + strategy);
    }
  }

  public static RevisionCreator createNew(Basis basis, TransactionExt transaction) {
    return RCBArtifactLogic.createNew(basis, transaction);
  }

  public synchronized void closeLocalChain(Transaction transaction) {
    closeLocalChain(transaction, null);
  }

  public synchronized void closeLocalChain(Transaction transaction, Procedure2<RevisionCreator, Transaction> additionalClosure) {
    rescan();
    assert myChains.getMainChain() != null;
    RevisionChain chain = myChains.getLastLocalChain();
    if (chain == null) {
      Log.debug("no local chains");
      return;
    }
    if (RCBUtil.isClosed(myBasis, chain, this)) {
      Log.debug(chain + " is closed");
      return;
    }

    final TransactionExt t = (TransactionExt) transaction; // todo remove cast
    final Revision lastMainChainRevision = myChains.getMainChain().getLastRevision();

    createClosure(t, additionalClosure);

    RevisionInternals internals = DBUtil.getInternals(lastMainChainRevision);

    Atom link = t.createAtom(true);
    link.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_RCB_CHAINS_LINK));
    link.buildJunction(Schema.KA_RCB_LINK_LOCALCHAIN, Particle.createLong(myBasis.getAtomID(chain)));
    link.buildJunction(Schema.KA_RCB_LINK_ENDLOCALBRANCH, Particle.createLong(internals.getAtom().getAtomID()));

    t.getEventSource().addStraightListener(new TransactionListener.Adapter() {
      public void onAfterUnderlyingCommit(Expansion underlying, boolean success) {
//        myLocalAccessChain.get().switchToMainChain();
        t.forceNotification(lastMainChainRevision);
        invalidateAllCaches();
      }
    });
  }

  private void createClosure(final TransactionExt t, Procedure2<RevisionCreator, Transaction> additionalClosureProcedure) {
    // create new revision - optimized code
    RevisionCreationContextBean bean = new RevisionCreationContextBean();
    bean.setArtifact(myBasis.getArtifact(myKey));
    bean.setRevisionChain(myLocalAccessChain.get());
    RevisionCreator closure = changeLocal(t, bean, null);
    bean.setRevisionCreator(closure);

    // copy into local revision all values
    Revision lastRev = myMainAccessChain.get().getLastRevision();
    MapIterator<ArtifactPointer, Value> ii = lastRev.getValues().iterator();
    while (ii.next()) {
      ArtifactPointer key = ii.lastKey();
      closure.setValue(key, ii.lastValue());
    }
    if (additionalClosureProcedure != null)
      additionalClosureProcedure.invoke(closure, t);
    closure.unsetValue(SystemObjects.ATTRIBUTE.MANUALLY_CHANGED_ATTRIBUTES);

    RevisionInternals internals = DBUtil.getInternals(closure.asRevision());
    internals.getAtom().buildJunction(Schema.KL_IS_CLOSURE, Particle.createLong(1));

    closure.forceCreation();
    t.forceNotification(closure.asRevision());
  }

  public /*not-synchronized!*/ WCN closeLocalChain(final TransactionControl transactionControl) {
    final WCN[] r = {null};
    WorkspaceUtils.repeatUntilNoCollisions(5, new DatabaseRunnable() {
      public void run() throws CollisionException {
        Transaction transaction = transactionControl.beginTransaction();
        closeLocalChain(transaction);
        transaction.commit();
        r[0] = transaction.getCommitWCN();
      }
    });
    return r[0];
  }

  public synchronized boolean hasConflict() {
    rescan();
    RevisionChain lastChain = RCBUtil.getLastOpenLocalChain(myBasis, myChains, this);
    if (lastChain == null)
      return false;
    long lastMainChainAtomID = myBasis.getAtomID(myChains.getMainChain().getLastRevision());
    if (RCBUtil.getOpenLink(myBasis, lastChain, this) == lastMainChainAtomID)
      return false;
    Index index = myBasis.getIndex(Schema.INDEX_RCB_MERGE);
    Atom atom = index.searchExact(new Long(myBasis.getAtomID(lastChain)));
    if (atom != null) {
      long linkID = atom.getLong(Schema.KA_MERGE_REMOTE_SOURCE);
      if (lastMainChainAtomID == linkID)
        return false;
    }
    return true;
  }

  public synchronized Revision getConflictBase(RevisionAccess strategy) {
    // todo refactor with markMerged
    assert strategy != null;
    if (strategy == RevisionAccess.ACCESS_DEFAULT)
      strategy = RevisionAccess.ACCESS_LOCAL;
    if (strategy != RevisionAccess.ACCESS_LOCAL && strategy != RevisionAccess.ACCESS_MAINCHAIN)
      throw new IllegalArgumentException("strategy == " + strategy);
    rescan();
    RevisionChain lastChain = RCBUtil.getLastOpenLocalChain(myBasis, myChains, this);
    if (lastChain == null)
      return null;
    Index index = myBasis.getIndex(Schema.INDEX_RCB_MERGE);
    long chainID = myBasis.getAtomID(lastChain);
    Atom atom = index.searchExact(new Long(chainID));
    if (atom == null) {
      Atom firstAtom = DBUtil.getInternals(lastChain.getFirstRevision()).getAtom();
      if (strategy == RevisionAccess.ACCESS_LOCAL) {
        return myBasis.getRevision(firstAtom.getAtomID(), myLocalAccessChain.get().getRevisionIterator());
      } else {
        long atomID = firstAtom.getLong(Schema.KA_COPIED_FROM);
        if (atomID < 0) {
          Log.warn("weird: local chain first revision is not a copy-atom");
          atomID = firstAtom.getAtomID();
        }
        return myBasis.getRevision(atomID, myMainAccessChain.get().getRevisionIterator());
      }
    } else {
      long atomID = atom.getLong(
        strategy == RevisionAccess.ACCESS_LOCAL ? Schema.KA_MERGE_LOCAL_RESULT : Schema.KA_MERGE_REMOTE_SOURCE);
      if (atomID < 0) {
        throw new IllegalStateException("bad merge atom");
      }
      boolean local = strategy == RevisionAccess.ACCESS_LOCAL;
      AbstractRevisionChain chain = local ? (AbstractRevisionChain) myLocalAccessChain.get() : myMainAccessChain.get();
      return myBasis.getRevision(atomID, chain.getRevisionIterator());
    }
  }

  public Map<ArtifactPointer, Value> getConflictChanges(RevisionAccess strategy) {
    Revision conflictBase = getConflictBase(strategy);
    if (conflictBase == null)
      return Collections15.emptyMap();
    return WorkspaceUtils.diff(conflictBase, getChain(strategy).getLastRevision());
  }


  public synchronized void markMerged(final Transaction transaction, Revision remoteSource,
    final Revision localResult)
  {
    // todo database inconsistency handler
    assert localResult != null;
    assert remoteSource != null;
    rescan();
    RevisionChain lastChain = RCBUtil.getLastOpenLocalChain(myBasis, myChains, this);
    if (lastChain == null)
      throw new IllegalStateException("there's no open chain");
    if (!DBUtil.getPhysicalChain(myBasis, localResult).equals(lastChain))
      throw new IllegalStateException(localResult + " is not on open local chain");
    RevisionChain mainChain = myChains.getMainChain();
    if (!DBUtil.getPhysicalChain(myBasis, remoteSource).equals(mainChain))
      throw new IllegalStateException(remoteSource + " is not on main chain");

    Index index = myBasis.getIndex(Schema.INDEX_RCB_MERGE);
    long chainID = myBasis.getAtomID(lastChain);
    Atom atom = index.searchExact(new Long(chainID));
    if (atom != null) {
      long prevSource = atom.getLong(Schema.KA_MERGE_REMOTE_SOURCE);
      if (prevSource >= 0) {
        Atom prevAtom = myBasis.ourUniverse.getAtom(prevSource);
        long prevSourceChain = prevAtom.getLong(Schema.KA_CHAIN_HEAD);
        if (prevSourceChain != myBasis.getAtomID(mainChain)) {
          // looks like previous merge was with the past incarnations
          assert hasBuriedChain(myChains.getBuriedChains(), prevSourceChain) :
            myKey + " :M: " + atom + " " + prevSourceChain;
        } else {
          if (!DBUtil.isEarlierOnChain(myBasis, remoteSource, prevSource))
            throw new IllegalStateException("database corrupt - merge data lost");
          long prevResult = atom.getLong(Schema.KA_MERGE_LOCAL_RESULT);
          if (prevResult >= 0 && !DBUtil.isEarlierOnChain(myBasis, localResult, prevResult))
            throw new IllegalStateException("database corrupt - merge data lost");
        }
      }
    }

    Atom mergeAtom = ((TransactionExt) transaction).createAtom(true);
    mergeAtom.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_RCB_MERGE));
    mergeAtom.buildJunction(Schema.KA_MERGE_CHAIN_HEAD, Particle.createLong(chainID));

    /**
     * Force creation of this revision, or we end up referring a non-existing atom
     */
    DBUtil.getInternals(localResult).forceCreation();

    mergeAtom.buildJunction(Schema.KA_MERGE_LOCAL_RESULT, Particle.createLong(myBasis.getAtomID(localResult)));
    mergeAtom.buildJunction(Schema.KA_MERGE_REMOTE_SOURCE, Particle.createLong(myBasis.getAtomID(remoteSource)));
    transaction.getEventSource().addStraightListener(new TransactionListener.Adapter() {
      public void onAfterUnderlyingCommit(Expansion underlying, boolean success) {
        ((TransactionExt) transaction).forceNotification(localResult);
      }
    });
  }

  private boolean hasBuriedChain(Collection<RevisionChain> buriedChains, long prevSourceChain) {
    for (Iterator<RevisionChain> ii = buriedChains.iterator(); ii.hasNext();) {
      RevisionChain chain = ii.next();
      if (myBasis.getAtomID(chain) == prevSourceChain)
        return true;
    }
    return false;
  }

  public /*not-synchronized!*/ void markMerged(TransactionControl transactionControl, Revision remoteSource,
    Revision localResult)
  {
    Transaction transaction = transactionControl.beginTransaction();
    markMerged(transaction, remoteSource, localResult);
    transaction.commitUnsafe();
  }

  public synchronized boolean hasOpenLocalBranch() {
    rescan();
    return RCBUtil.getLastOpenLocalChain(myBasis, myChains, this) != null;
  }

  public synchronized Revision getLocalChainStartingRevision(Revision localRevision) {
    rescan();
    RevisionChain physicalChain = DBUtil.getPhysicalChain(myBasis, localRevision);
    List<RevisionChain> localChains = myChains.getLocalChains();
    for (RevisionChain localChain : localChains) {
      if (localChain.equals(physicalChain)) {
        return localChain.getFirstRevision();
      }
    }
    Log.warn("revision " + localRevision + " is not on a local chain");
    return null;
  }

  public synchronized Map<ArtifactPointer, Value> getLocalChanges() {
    // todo take merge into account
    rescan();
    RevisionChain lastChain = RCBUtil.getLastOpenLocalChain(myBasis, myChains, this);
    if (lastChain == null)
      return Collections15.emptyMap();
    return WorkspaceUtils.diff(lastChain.getFirstRevision(), lastChain.getLastRevision());
  }

  public synchronized Map<ArtifactPointer, Value> getLocalChanges(Revision revision) {
    Revision firstRevision;
    synchronized (this) {
      rescan();
      if (revision == null)
        return Collections15.emptyMap();
      RevisionChain chain = DBUtil.getPhysicalChain(myBasis, revision);
      if (!myChains.getLocalChains().contains(chain))
        return Collections15.emptyMap();
      firstRevision = chain.getFirstRevision();
    }
    return WorkspaceUtils.diff(firstRevision, revision);
  }


  public synchronized int getIncarnation() {
    rescan();
    return myChains.getIncarnations();
  }

  public synchronized RevisionChain getBuriedChain(int incarnation) {
    rescan();
    return myChains.getBuriedChain(incarnation);
  }

  public synchronized RevisionCreator startReincarnation(Transaction transaction) {
    rescan();
    if (myReincarnation == null) {
      myReincarnation =
        CommonArtifactLogic.createSingleChain(myBasis, (TransactionExt) transaction, Schema.ATOM_CHAIN_HEAD);
      Atom atom = DBUtil.getInternals(myReincarnation.asRevision()).getAtom();
      atom.buildJunction(Schema.KA_CHAIN_HEAD, Particle.createLong(atom.getAtomID()));
      atom.buildJunction(Schema.KA_CHAIN_ARTIFACT, Particle.createLong(myKey));
      atom.buildJunction(Schema.KL_REINCARNATION, Particle.createLong(myChains.getIncarnations() + 1));
      myReincarnationRelinks = Collections15.hashMap();
    }
    return myReincarnation;
  }

  public synchronized SortedSet<Relink> getReincarnationRequiredRelinks() {
    rescan();
    checkReincarnating(null);
    SortedSet<Relink> result = Collections15.treeSet();
    Index indexEnd = myBasis.getIndex(Schema.INDEX_RCB_ENDLINK_MC);
    Index indexStart = myBasis.getIndex(Schema.INDEX_RCB_STARTLINK_MC);
    Revision revision = myChains.getMainChain().getLastRevision();
    while (revision != null) {
      long atomID = myBasis.getAtomID(revision);
      addRelinks(result, indexEnd.search(new Long(atomID)), atomID, Schema.KA_RCB_LINK_ENDLOCALBRANCH);
      addRelinks(result, indexStart.search(new Long(atomID)), atomID, Schema.KA_RCB_LINK_BEGINLOCALBRANCH);
      revision = revision.getPrevRevision();
    }
    return result;
  }

  public synchronized void finishReincarnation(Transaction transaction) {
    checkReincarnating(null);
    rescan();
    // check links
    final TransactionExt t = (TransactionExt) transaction; // todo remove cast
    final Atom binderAtom = t.createAtom(true);
    binderAtom.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_RCB_BINDER));
    binderAtom.buildJunction(Schema.KA_PREV_ATOM, Particle.createLong(myChains.getLastBinderAtomID()));
    binderAtom.buildJunction(Schema.KA_CHAIN_HEAD, Particle.createLong(myKey));
    binderAtom.buildJunction(Schema.KL_RCB_BINDER_REFTYPE, Particle.createLong(Schema.RCB_BINDER_REFTYPE_MAINCHAIN));
    long chainStart = myBasis.getAtomID(myReincarnation.asRevision());
    binderAtom.buildJunction(Schema.KA_RCB_BINDER_CHAINSTART, Particle.createLong(chainStart));

//    myRescanNeeded = true;
    t.getEventSource().addStraightListener(new TransactionListener.Adapter() {
      public void onBeforeUnderlyingCommit(Expansion underlying) {
        underlying.addVerifier(new VerifyPhysicalChainLastAtom(myBasis, binderAtom));
      }

      public void onAfterUnderlyingCommit(Expansion underlying, boolean success) {
        myReincarnationRelinks = null;
        myReincarnation = null;
        myRescanNeeded = true;
        Revision newMainChainLastRevision = getChain(RevisionAccess.ACCESS_MAINCHAIN).getLastRevision();
        t.forceNotification(newMainChainLastRevision);
        invalidateAllCaches();
      }
    });
  }

  public /*not-synchronized!*/ void finishReincarnation(TransactionControl transactionControl) {
    checkReincarnating(null);
    Transaction transaction = transactionControl.beginTransaction();
    finishReincarnation(transaction);
    transaction.commitUnsafe();
  }

  public synchronized boolean isReincarnating(Revision revision) {
    if (myReincarnation == null)
      return false;
    long chainID = myBasis.getAtomID(revision.getChain());
    return chainID == myBasis.getAtomID(myReincarnation.asRevision());
  }

  public synchronized boolean isReincarnating() {
    return myReincarnation != null;
  }

  public synchronized RevisionCreator getReincarnationCreator() {
    checkReincarnating(null);
    return myReincarnation;
  }

  public synchronized void cancelReincarnation() {
    checkReincarnating(null);
    myReincarnation = null;
    myReincarnationRelinks = null;
  }

  public synchronized long getLastIncarnationUcn() {
    rescan();
    return myChains.getIncarnationUcn();
  }

  public synchronized boolean isLocalRevision(Revision revision) {
    rescan();
    RevisionWithInternals phys = myBasis.getRevision(myBasis.getAtomID(revision), PhysicalRevisionIterator.INSTANCE);
    long copiedFrom = Schema.KA_COPIED_FROM.get(phys.getAtom());
    if (copiedFrom > 0) {
      // this is a local chain copy of a main chain atom
      // since it reflects the main chain atom in the local branchm it is not a local revision
      // fix for #965
      return false;
    }
    RevisionChain chain = phys.getChain();
    return myChains.getLocalChains().contains(chain);
  }

  public synchronized void rescan() {
    Threads.assertLongOperationsAllowed();
    if (!myRescanNeeded)
      return;
    while (true) {
      try {
        rescanBinderChain();
        myRescanNeeded = false;
        break;
      } catch (DatabaseInconsistentException e) {
        myBasis.ourConsistencyWrapper.handle(e, -1);
      }
    }
  }

  public synchronized void rescanUnsafe() throws DatabaseInconsistentException {
    Threads.assertLongOperationsAllowed();
    if (!myRescanNeeded)
      return;
    rescanBinderChain();
    myRescanNeeded = false;
  }

  public RevisionChain getChain(RevisionAccess strategy) {
    // default is local
    if (strategy == RevisionAccess.ACCESS_DEFAULT)
      strategy = RevisionAccess.ACCESS_LOCAL;
    rescan();
    if (strategy == RevisionAccess.ACCESS_MAINCHAIN) {
      return myMainAccessChain.get();
    } else if (strategy == RevisionAccess.ACCESS_LOCAL) {
      return myLocalAccessChain.get();
    } else {
      throw new UnsupportedOperationException("strategy " + strategy + " is not supported");
    }
  }

  public boolean isAccessStrategySupported(RevisionAccess strategy) {
    return strategy == RevisionAccess.ACCESS_DEFAULT || strategy == RevisionAccess.ACCESS_LOCAL ||
      strategy == RevisionAccess.ACCESS_MAINCHAIN;
  }

  public synchronized boolean containsRevision(Revision revision) {
    rescan();
    if (myChains.getMainChain().containsRevision(revision))
      return true;
    List<RevisionChain> localChains = myChains.getLocalChains();
    for (int i = 0; i < localChains.size(); i++) {
      RevisionChain chain = localChains.get(i);
      if (chain.containsRevision(revision))
        return true;
    }
    return false;
  }

  public boolean isValid() {
    try {
      rescanUnsafe();
      return true;
    } catch (DatabaseInconsistentException e) {
      return false;
    }
  }

  private void addRelinks(SortedSet<Relink> relinks, Iterator<Atom> atoms, long atomID, LongJunctionKey key) {
    while (atoms.hasNext()) {
      Atom link = atoms.next();
      if (link.getLong(key) != atomID)
        return;
      Long relinkKey = new Long(link.getAtomID());
      Relink relink = myReincarnationRelinks.get(relinkKey);
      if (relink == null) {
        relink = new RelinkImpl(link, key);
        myReincarnationRelinks.put(relinkKey, relink);
      }
      relinks.add(relink);
    }
  }

  private synchronized RevisionCreator changeLocal(TransactionExt transaction, RevisionCreationContextBean bean,
    Revision suggestedBase)
  {
    // todo refactor long method
    rescan();
    assert myChains.getMainChain() != null;
    RevisionChain lastLocalChain = myChains.getLastLocalChain();
    Atom revisionAtom = transaction.createAtom(false);
    Revision baseRevision = getChain(RevisionAccess.ACCESS_LOCAL).getLastRevision();
    if (lastLocalChain == null || RCBUtil.isClosed(myBasis, lastLocalChain, this)) {
      // new local chain starting from last main chain revision

      if (suggestedBase != null) {
        // change base revision to suggested or report an error, if suggestion is illegal
        if (isLocalRevision(suggestedBase))
          throw new IllegalBaseRevisionException(suggestedBase);

        // This revision is "earliest" that could be used as a base, because if there was a local
        // branch, we can't make another branch below it.
        Revision minRev = lastLocalChain == null ? null :
          myBasis.getRevision(RCBUtil.getCloseLink(myBasis, lastLocalChain, this), PhysicalRevisionIterator.INSTANCE);

        while (true) {
          if (baseRevision.equals(suggestedBase))
            break;
          if (baseRevision.equals(minRev))
            throw new IllegalArgumentException(
              "cannot change artifact based on a revision earlier than last local chain");
          baseRevision = baseRevision.getPrevRevision();
          if (baseRevision == null) {
            throw new IllegalArgumentException("cannot change artifact based on revision from another artifact");
          }
          assert!isLocalRevision(baseRevision) : baseRevision;
        }
      }

      final Atom binderElement = transaction.createAtom(true);
      binderElement.buildJunction(Schema.KA_CHAIN_HEAD, Particle.createLong(myKey));
      binderElement.buildJunction(Schema.KL_RCB_BINDER_REFTYPE,
        Particle.createLong(Schema.RCB_BINDER_REFTYPE_LOCALCHAIN));
      binderElement.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_RCB_BINDER));
      binderElement.buildJunction(Schema.KA_PREV_ATOM, Particle.createLong(myChains.getLastBinderAtomID()));

      long lastRevisionAtomID = baseRevision.getKey();
      Atom copyAtom = transaction.createAtom(true);
      binderElement.buildJunction(Schema.KA_RCB_BINDER_CHAINSTART, Particle.createLong(copyAtom.getAtomID()));
      copyAtom.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_CHAIN_HEAD));
      copyAtom.buildJunction(Schema.KA_CHAIN_HEAD, Particle.createLong(copyAtom.getAtomID()));
      copyAtom.buildJunction(Schema.KA_CHAIN_ARTIFACT, Particle.createLong(myKey));
      MapIterator<ArtifactPointer, Value> ii = baseRevision.getValues().iterator();
      while (ii.next()) {
        long key = ii.lastKey().getPointerKey();
        Particle particle = Particle.create(myBasis.ourValueFactory.marshall(ii.lastValue()));
        copyAtom.buildJunction(key, particle);
      }
      copyAtom.buildJunction(Schema.KA_COPIED_FROM, Particle.createLong(lastRevisionAtomID));

      Atom linkAtom = transaction.createAtom(true);
      linkAtom.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_RCB_CHAINS_LINK));
      linkAtom.buildJunction(Schema.KA_RCB_LINK_LOCALCHAIN, Particle.createLong(copyAtom.getAtomID()));
      linkAtom.buildJunction(Schema.KA_RCB_LINK_BEGINLOCALBRANCH, Particle.createLong(lastRevisionAtomID));

      revisionAtom.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_REVISION));
      revisionAtom.buildJunction(Schema.KA_CHAIN_HEAD, Particle.createLong(copyAtom.getAtomID()));
      revisionAtom.buildJunction(Schema.KA_PREV_ATOM, Particle.createLong(copyAtom.getAtomID()));

      transaction.getEventSource().addStraightListener(new TransactionListener.Adapter() {
        public void onBeforeUnderlyingCommit(Expansion underlying) {
          underlying.addVerifier(new VerifyPhysicalChainLastAtom(myBasis, binderElement));
        }

        public void onAfterUnderlyingCommit(Expansion underlying, boolean success) {
          synchronized (RCBArtifactImpl.this) {
            myRescanNeeded = true;
          }
        }
      });
    } else {
      // update existing open local chain
      //baseRevision = lastLocalChain.getLastRevision();
      if (suggestedBase != null) {
        if (!baseRevision.equals(suggestedBase)) {
          Log.warn("requested to start a new revision from an older revision on a local chain, ignoring (" +
            suggestedBase + " " + baseRevision + ")");
          // The following code has been here before. It is removed because of bugs #713 and
          // #709 
          //          throw new IllegalArgumentException(
          //    "cannot change artifact with an open local chain based on anything but the last open chain revision");
        }
      }

      revisionAtom.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_REVISION));
      revisionAtom.buildJunction(Schema.KA_CHAIN_HEAD, Particle.createLong(lastLocalChain.getKey()));
      revisionAtom.buildJunction(Schema.KA_PREV_ATOM, Particle.createLong(baseRevision.getKey()));
    }

    return new RevisionCreatorFacade(myBasis, revisionAtom, baseRevision, bean, transaction.getEventSource());
  }

  private void checkReincarnating(Revision revision) {
    if (myReincarnation == null)
      throw new IllegalStateException("reincarnation is not in progress");
    if (revision != null && !isReincarnating(revision))
      throw new IllegalArgumentException("revision is not on the reincarnation chain: " + revision);
  }

  private void invalidateAllCaches() {
    invalidateCachesForChain(RevisionAccess.ACCESS_LOCAL);
    invalidateCachesForChain(RevisionAccess.ACCESS_MAINCHAIN);
  }

  private void invalidateCachesForChain(RevisionAccess chainAccess) {
    RevisionChain chain = getChain(chainAccess);
    Revision revision = chain.getLastRevision();
    while (revision != null) {
      RevisionInternals internals = DBUtil.getInternals(revision);
      for (int i = 0; i < RevisionAccess.ALL.length; i++)
        internals.invalidateValuesCache(RevisionAccess.ALL[i]);
      revision = revision.getPrevRevision();
    }
  }

  private synchronized void rescanBinderChain() throws DatabaseInconsistentException {
    myChains = null;
    RevisionChain mainChain = null;
    long incarnationUcn = -1;
    int incarnation = 0;
    long lastBinderAtomID = -1;
    List<RevisionChain> localChains = null;
    List<RevisionChain> buriedChains = null;

    Index index = myBasis.getIndex(Schema.INDEX_KA_CHAIN_HEAD);
    long binderAtom = myKey;
    Iterator<Atom> ii = index.search(binderAtom);
    while (ii.hasNext()) {
      Atom atom = ii.next();
      if (atom.getLong(Schema.KA_CHAIN_HEAD) != binderAtom)
        break;
      long refType = atom.getLong(Schema.KL_RCB_BINDER_REFTYPE);
      if (refType == -1) {
        Log.warn("no reftype in binder chain, atom " + atom);
        continue;
      }
      if (lastBinderAtomID < 0)
        lastBinderAtomID = atom.getAtomID();
      boolean isMainChain;
      if (Schema.RCB_BINDER_REFTYPE_MAINCHAIN.equals(refType)) {
        isMainChain = true;
      } else if (Schema.RCB_BINDER_REFTYPE_LOCALCHAIN.equals(refType)) {
        isMainChain = false;
      } else {
        throw new DatabaseInconsistentException("reftype " + refType + " atom " + atom);
      }
      long refAtom = atom.getLong(Schema.KA_RCB_BINDER_CHAINSTART);
      if (refAtom < 0)
        throw new DatabaseInconsistentException("refatom " + refAtom + " atom " + atom);

      if (isMainChain) {
        incarnation++;
        RevisionChain chain = myBasis.getPhysicalChain(refAtom);
        if (mainChain == null) {
          mainChain = chain;
          incarnationUcn = atom.getUCN();
        } else {
          if (buriedChains == null)
            buriedChains = Collections15.arrayList();
          buriedChains.add(chain);
        }
      } else {
        if (localChains == null)
          localChains = Collections15.arrayList();
        localChains.add(myBasis.getPhysicalChain(refAtom));
      }
    }

    if (mainChain == null)
      throw new DatabaseInconsistentException(this + ": no main chain");
    if (incarnationUcn <= 0)
      throw new DatabaseInconsistentException(this + ": no incarnation ucn");

    Map<Integer, RevisionChain> buriedChainsMap;
    if (buriedChains == null) {
      buriedChainsMap = null;
    } else {
      assert incarnation == buriedChains.size() + 1;
      buriedChainsMap = Collections15.hashMap();
      for (int i = 0; i < buriedChains.size(); i++)
        buriedChainsMap.put(i + 1, buriedChains.get(i));
    }
    myChains =
      new RCBPhysicalChains(mainChain, localChains, incarnation, lastBinderAtomID, buriedChainsMap, incarnationUcn);
    synchronized (myLocalAccessChain.getLock()) {
      if (myLocalAccessChain.isInitialized())
        myLocalAccessChain.get().setPhysicalChains(myChains);
    }
    synchronized (myMainAccessChain.getLock()) {
      if (myMainAccessChain.isInitialized())
        myMainAccessChain.get().setMainChain(myChains.getMainChain());
    }
  }

  private static RCBArtifactImpl getImpl(Artifact artifact) {
    RCBArtifact extension = artifact.getRCBExtension(true);
    if (extension instanceof RCBArtifactImpl)
      return (RCBArtifactImpl) extension;
    throw new Failure("artifact " + artifact);
  }

  public static boolean checkStrategy(RevisionCreator creator, RevisionAccess strategy) {
    if (strategy == RevisionAccess.ACCESS_DEFAULT)
      strategy = RevisionAccess.ACCESS_LOCAL;
    RevisionChain chain = creator.asRevision().getChain();
    boolean local = chain instanceof RCBLocalChain;
    return strategy == (local ? RevisionAccess.ACCESS_LOCAL : RevisionAccess.ACCESS_MAINCHAIN);
  }

  public synchronized List<Revision> getCompleteRevisionsList(List<Revision> recipient) {
    rescan();
    if (recipient == null)
      recipient = Collections15.arrayList();

    myChains.getMainChain().getCompleteRevisionsList(recipient);

    List<RevisionChain> chains = myChains.getLocalChains();
    for (int i = 0; i < chains.size(); i++)
      chains.get(i).getCompleteRevisionsList(recipient);

    for (Iterator<RevisionChain> ii = myChains.getBuriedChains().iterator(); ii.hasNext();)
      ii.next().getCompleteRevisionsList(recipient);

    return recipient;
  }

  private class RelinkImpl implements Relink, Comparable<Relink> {
    private final Atom myOldLink;
    private final LongJunctionKey myKey;
    private final Particle myLocalChainRef;
    private final long myMainChainAtomID;
    private final long myUCN;

    private RelinkImpl(Atom oldLink, LongJunctionKey key) {
      myOldLink = oldLink;
      myKey = key;
      myUCN = myOldLink.getUCN();
      myMainChainAtomID = myOldLink.getLong(myKey);
      if (myMainChainAtomID < 0)
        throw new Failure("todo");
      myLocalChainRef = myOldLink.get(Schema.KA_RCB_LINK_LOCALCHAIN.getKey());
    }

    public boolean isBranchEnd() {
      assert myKey == Schema.KA_RCB_LINK_ENDLOCALBRANCH || myKey == Schema.KA_RCB_LINK_BEGINLOCALBRANCH;
      return myKey == Schema.KA_RCB_LINK_ENDLOCALBRANCH;
    }

    public int compareTo(Relink relink) {
      RelinkImpl that = (RelinkImpl) relink;
      int c = Containers.compareLongs(myUCN, that.myUCN);
      if (c != 0)
        return c;
      else
        return Containers.compareLongs(myMainChainAtomID, that.myMainChainAtomID);
    }

    public Revision getOldRevision() {
      return myBasis.getRevision(myMainChainAtomID, myMainAccessChain.get().getRevisionIterator());
    }

    public WCN getOldLinkWCN() {
      return WCN.createWCN(myUCN);
    }

    public void relink(Transaction transaction, Revision newRevision) {
      synchronized (RCBArtifactImpl.this) {
        checkReincarnating(newRevision);
        TransactionExt t = (TransactionExt) transaction; // todo remove cast

        Atom newLink = t.createAtom(true);
        newLink.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_RCB_CHAINS_LINK));
        newLink.buildJunction(Schema.KA_RCB_LINK_LOCALCHAIN, myLocalChainRef);
        newLink.buildJunction(myKey, Particle.createLong(myBasis.getAtomID(newRevision)));

        if (isBranchEnd()) {
          createRelinkClosure(t, newRevision);
        }
      }
    }

    private void createRelinkClosure(final TransactionExt t, Revision newRevision) {
      assert myLocalChainRef instanceof Particle.PLong : myLocalChainRef;
      RevisionChain relinked = myBasis.getPhysicalChain(((Particle.PLong) myLocalChainRef).getValue());
      RevisionInternals lastRelinked = DBUtil.getInternals(relinked.getLastRevision());
      Atom lastAtom = lastRelinked.getAtom();
      long isClosure = lastAtom.getLong(Schema.KL_IS_CLOSURE);
      assert isClosure == 1 : isClosure + " " + lastAtom;

      Atom relinkClosure = t.createAtom(true);
      relinkClosure.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_REVISION));
      relinkClosure.buildJunction(Schema.KA_CHAIN_HEAD, myLocalChainRef);
      relinkClosure.buildJunction(Schema.KA_PREV_ATOM, Particle.createLong(lastAtom.getAtomID()));
      relinkClosure.buildJunction(Schema.KL_IS_CLOSURE, Particle.createLong(1));
      relinkClosure.buildJunction(Schema.KL_IS_CLOSURE_RELINKED, Particle.createLong(1));

      // copy into local revision all values
      Revision lastRev = newRevision;
      MapIterator<ArtifactPointer, Value> ii = lastRev.getValues().iterator();
      while (ii.next()) {
        ArtifactPointer key = ii.lastKey();
        Value value = ii.lastValue();
        long attrAtom = myBasis.getAtomID(key.getArtifact());
        Particle particle = Particle.create(myBasis.ourValueFactory.marshall(value));
        relinkClosure.buildJunction(attrAtom, particle);
      }

      relinkClosure.setReferred();
    }


    public void relink(TransactionControl transactionControl, Revision newRevision) {
      Transaction transaction = transactionControl.beginTransaction();
      relink(transaction, newRevision);
      transaction.commitUnsafe();
    }
  }
}
