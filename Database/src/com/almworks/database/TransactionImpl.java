package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.TypedArtifact;
import com.almworks.api.database.util.WorkspaceUtils;
import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Expansion;
import com.almworks.database.objects.LocalArtifact;
import com.almworks.database.objects.RevisionCreatorFacade;
import com.almworks.database.objects.remote.RCBArtifactImpl;
import com.almworks.database.typed.TypedObjectFactory;
import com.almworks.util.SequenceRunner;
import com.almworks.util.commons.Procedure;
import com.almworks.util.events.*;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.threads.Threads;
import gnu.trove.TLongObjectHashMap;
import gnu.trove.TLongObjectIterator;
import org.almworks.util.*;
import util.concurrent.Synchronized;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class TransactionImpl implements TransactionExt {
  private static final TypedKey<SortedMap<WCN, Runnable>> NOTIFICATIONS = TypedKey.create("notifications");
  private static final RevisionCreator[] EMPTY = {};

  private final FireEventSupport<TransactionListener> myEventSupport =
    FireEventSupport.createSynchronized(TransactionListener.class);
  private final TransactionControlExt myTransactionController;
  private final Basis myBasis;
  private TLongObjectHashMap/*<RevisionCreator>*/ myChanging = new TLongObjectHashMap(1);
  private Expansion myUnderlying;
  private WCN myCommitWCN;
  private boolean myForcedNotEmpty = false;
  private List<Revision> myNotificationForced = null;

  private final Synchronized<State> myState = new Synchronized<State>(State.PENDING, this);
  private static int ourNumber = 0;
  private final int myNumber;

  {
    synchronized (TransactionImpl.class) {
      myNumber = ++ourNumber;
    }
  }

  TransactionImpl(Basis basis, TransactionControlExt transactionController) {
    if (transactionController == null)
      throw new NullPointerException("transactionController");
    myTransactionController = transactionController;
    myBasis = basis;
    myUnderlying = basis.ourUniverse.begin();
  }

  public synchronized RevisionCreator createArtifact(ArtifactFeature[] features) {
    Threads.assertLongOperationsAllowed();
    checkWorking();
    RevisionCreator creator = null;
    if (features == ArtifactFeature.LOCAL_ARTIFACT) {
      creator = LocalArtifact.createNew(myBasis, this);
    } else if (features == ArtifactFeature.REMOTE_ARTIFACT) {
      creator = RCBArtifactImpl.createNew(myBasis, this);
    } else {
      throw new IllegalStateException("cannot understand feature set");
    }
    assert creator != null;
    myChanging.put(creator.getArtifact().getKey(), creator);
    return creator;
  }

  public synchronized RevisionCreator createArtifact() {
    return createArtifact(ArtifactFeature.LOCAL_ARTIFACT);
  }


  public synchronized RevisionCreator changeArtifact(ArtifactPointer artifact) {
    return changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT, null);
  }

  public synchronized RevisionCreator changeArtifact(ArtifactPointer pointer, RevisionAccess strategy) {
    return changeArtifact(pointer, strategy, null);
  }

  public RevisionCreator changeArtifact(ArtifactPointer pointer, RevisionAccess strategy, Revision baseRevision) {
    while (true) {
      try {
        Threads.assertLongOperationsAllowed();
        checkWorking();
        Artifact artifact = pointer.getArtifact();
        long key = artifact.getKey();
        RevisionCreator creator = (RevisionCreator) myChanging.get(key);
        if (creator == null) {
          // todo - better? another hierarchy with "artifact logic" ?
          if (isMultiChainArtifact(artifact)) {
            creator = RCBArtifactImpl.change(myBasis, this, artifact, strategy, baseRevision);
          } else {
            creator = LocalArtifact.change(myBasis, this, artifact, strategy, baseRevision);
          }
          myChanging.put(key, creator);
        } else {
          boolean correctStrategy = isMultiChainArtifact(artifact) ? RCBArtifactImpl.checkStrategy(creator, strategy) :
            LocalArtifact.checkStrategy(creator, strategy);
          if (!correctStrategy)
            throw new IllegalArgumentException(
              "artifact is already changing in this transaction with another strategy");

          // check correct baseRevision
          if (baseRevision != null) {
            if (!baseRevision.equals(creator.asRevision().getPrevRevision()))
              throw new IllegalArgumentException("artifact is already changing with another base revision");
          }
        }
        return creator;
      } catch (DatabaseInconsistentException e) {
        myBasis.ourConsistencyWrapper.handle(e, -1);
      }
    }
  }

  private boolean isMultiChainArtifact(Artifact artifact) {
    return artifact.getRCBExtension(false) != null;
  }

  public synchronized <T extends TypedArtifact> RevisionCreator createArtifact(final Class<T> typedClass) {
    Threads.assertLongOperationsAllowed();
    while (true) {
      try {
        TypedObjectFactory factory = myBasis.getTypedObjectFactory(typedClass);
        if (factory == null)
          throw new DatabaseInconsistentException("factory is null for class " + typedClass);
        RevisionCreator newObject = createArtifact();
        TypedArtifact typedArtifact = factory.initializeTyped(myBasis, newObject);
        if (typedArtifact == null || !(typedClass.isAssignableFrom(typedArtifact.getClass())))
          throw new DatabaseInconsistentException("bad typed artifact was created by factory for class " + typedClass);
        return newObject;
      } catch (DatabaseInconsistentException e) {
        myBasis.ourConsistencyWrapper.handle(e, -1);
      }
    }
  }

  public synchronized boolean isChanging(ArtifactPointer object) {
    if (!isWorking())
      return false;
    return myChanging.containsKey(object.getArtifact().getKey());
  }

  public synchronized RevisionCreator[] getPendingChanges() {
    if (!isWorking())
      return EMPTY;
    int size = myChanging.size();
    RevisionCreator[] result = new RevisionCreator[size];
    int i = 0;
    for (TLongObjectIterator ii = myChanging.iterator(); ii.hasNext();) {
      ii.advance();
      result[i++] = (RevisionCreator) ii.value();
    }
    return result;
  }

  public synchronized RevisionCreator getPendingCreator(Transaction transaction, Filter.Equals filter) {
    if (!isWorking())
      return null;
    Value sample = filter.getValue();
    if (sample == null)
      return null;
    for (TLongObjectIterator ii = myChanging.iterator(); ii.hasNext();) {
      ii.advance();
      RevisionCreator creator = (RevisionCreator) ii.value();
      Value value = creator.getChangingValue(filter.getAttribute());
      if (value != null && value.equals(sample))
        return creator;
    }
    return null;
  }

  public void commitUnsafe() throws UnsafeCollisionException {
    Threads.assertLongOperationsAllowed();
    try {
      new Commit().commit();
    } catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
  }

  public void commit() throws CollisionException {
    try {
      commitUnsafe();
    } catch (UnsafeCollisionException e) {
      throw new CollisionException(e.getCause());
    }
  }

  private boolean isOngoingReincarnation(Revision revision) {
    // todo incapsulate logic
    // kludge hyper-dependency
    Artifact artifact = revision.getArtifact();
    RCBArtifact rcb = artifact.getRCBExtension(false);
    if (rcb != null) {
// Reincarnation revision belongs to a chain
      return rcb.isReincarnating(revision);
    }
    return false;
  }

  public synchronized void rollback() {
    if (!myState.commit(State.PENDING, State.ROLLED_BACK))
      return;
    cleanUp();
  }

  public synchronized boolean isWorking() {
    return myState.get() == State.PENDING;
  }

  public synchronized boolean isCommitted() {
    return myState.get() == State.COMMITTED;
  }

  public synchronized boolean isRolledBack() {
    return myState.get() == State.ROLLED_BACK;
  }

  public synchronized boolean isEmpty() {
    State state = myState.get();
    if (state != State.PENDING && state != State.COMMITTING)
      return true;
    if (myForcedNotEmpty)
      return false;
    for (TLongObjectIterator ii = myChanging.iterator(); ii.hasNext();) {
      ii.advance();
      RevisionCreator creator = (RevisionCreator) ii.value();
      if (!creator.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public synchronized void forceNotEmpty() {
    if (!isWorking())
      throw new IllegalStateException("not working");
    myForcedNotEmpty = true;
  }

  public EventSource<TransactionListener> getEventSource() {
    return myEventSupport;
  }

  public synchronized WCN getCommitWCN() {
    if (myState.get() != State.COMMITTED)
      return null;
    return myCommitWCN;
  }


  private synchronized void cleanUp() {
    myChanging.clear();
    myEventSupport.noMoreEvents();
    myTransactionController.onTransactionFinished();
  }

  private void checkWorking() {
    if (!isWorking())
      throw new IllegalStateException(this + " is over (" + myState.get() + ")");
  }

  public void verifyViewNotChanged(ArtifactView view) {
    // todo
  }

  public synchronized Atom createAtom(boolean forceCreation) {
    checkWorking();
    if (forceCreation) {
      forceNotEmpty();
    }
    return myUnderlying.createAtom();
  }

  public synchronized void forceNotification(Revision revision) {
    if (myNotificationForced == null)
      myNotificationForced = Collections15.arrayList();
    myNotificationForced.add(revision);
  }

  public String toString() {
    return "Transaction:" + myNumber;
  }


  private final class Commit implements Procedure<Atom[]> {
    private RevisionCreator[] mymyChanging = null;
    private boolean mymyEmpty = true;
    private boolean mymySuccess = false;
    private TransactionListener mymyDispatcher = null;
    private TransactionListener mymyOnCommitDispatcher = null;
    private Set<Revision> mymyAnnouncedRevisions = null;
    private Set<Artifact> mymyAnnouncedArtifacts = null;
    private Atom[] mymyCommittedAtoms = null;
    private ProcessingLock mymyCommitLock;

    public void commit() throws InterruptedException {
      if (!init())
        return;
      try {
        fireBeforeUnderlyingCommit();
        doUnderlyingCommit();
        fireAfterUnderlyingCommit();
        callAtomHooksAndAdvanceSystemWCN();
        collectAnnouncedRevisions();
        setCommitted();
        fireNewRevisionsAppeared();
        collectAnnouncedArtifacts();
        prepareToFireCommitted();
      } finally {
        try {
          processNotifications();
          releaseCommitLock();
          cleanUp();
          mymyCommittedAtoms = null;
        } catch (Exception e) {
          Log.error("exception in finally block", e);
        }
      }
    }

    private void releaseCommitLock() {
      ProcessingLock commitLock = mymyCommitLock;
      mymyCommitLock = null;
      if (commitLock != null) {
        commitLock.release(TransactionImpl.this);
      }
    }

    private void callAtomHooksAndAdvanceSystemWCN() {
      if (mymyCommittedAtoms != null && mymyCommittedAtoms.length > 0)
        myTransactionController.callCommittedAtomsHook(TransactionImpl.this, mymyCommittedAtoms);
      myTransactionController.onLateWCNPromotion(myCommitWCN);
    }

    private void processNotifications() {
      SequenceRunner runner = null;
      synchronized (NOTIFICATIONS) {
        SortedMap<WCN, Runnable> notifications = getNotifications();
        if (myCommitWCN != null && notifications.get(myCommitWCN) == null) {
          // we're finishing, so if we don't have a runnable for us yet, then we're most probably
          // throwing an error. remove my object so other transactions can be fired.
          notifications.remove(myCommitWCN);
        }
        for (Iterator<Runnable> ii = notifications.values().iterator(); ii.hasNext();) {
          Runnable runnable = ii.next();
          if (runnable == null) {
            // still waiting for runnable
            break;
          }
          if (runner == null)
            runner = new SequenceRunner();
          runner.add(runnable);
          ii.remove();
        }
      }
      if (runner != null)
        runner.runAndClear();
    }

    private void prepareToFireCommitted() {
      synchronized (NOTIFICATIONS) {
        SortedMap<WCN, Runnable> notifications = getNotifications();
        if (mymyOnCommitDispatcher != null && mymyAnnouncedArtifacts != null && mymyAnnouncedArtifacts.size() > 0) {
          final TransactionListener dispatcher = mymyOnCommitDispatcher;
          final Set<Artifact> announcedArtifacts = mymyAnnouncedArtifacts;
          final ProcessingLock commitLock = mymyCommitLock;
          assert myCommitWCN != null;
          assert commitLock != null;
          assert notifications.containsKey(myCommitWCN) && notifications.get(myCommitWCN) == null : "no placeholder";
          final LockOwner lock = new LockOwner("NT", TransactionImpl.this);
          commitLock.lock(lock);
          notifications.put(myCommitWCN, new Runnable() {
            public void run() {
              try {
                dispatcher.onCommit(TransactionImpl.this, announcedArtifacts, commitLock);
              } finally {
                commitLock.release(lock);
              }
            }
          });
        }
      }
    }

    private void collectAnnouncedArtifacts() {
      if (mymyOnCommitDispatcher != null && mymyAnnouncedRevisions != null) {
        mymyAnnouncedArtifacts = Collections15.hashSet();
        for (Iterator<Revision> ii = mymyAnnouncedRevisions.iterator(); ii.hasNext();) {
          Revision revision = ii.next();
          if (!isOngoingReincarnation(revision))
            mymyAnnouncedArtifacts.add(revision.getArtifact());
        }
        mymyAnnouncedArtifacts = Collections.unmodifiableSet(mymyAnnouncedArtifacts);
      }
    }

    private void setCommitted() {
      if (!myState.commit(State.COMMITTING, State.COMMITTED)) {
        assert false : "cannot be - " + myState;
      }
    }

    private void fireNewRevisionsAppeared() {
      if (mymyDispatcher != null && mymyAnnouncedRevisions != null && mymyAnnouncedRevisions.size() > 0)
        mymyDispatcher.onNewRevisionsAppeared(TransactionImpl.this, mymyAnnouncedRevisions);
    }

    private void fireAfterUnderlyingCommit() {
      if (mymyDispatcher != null)
        mymyDispatcher.onAfterUnderlyingCommit(myUnderlying, mymySuccess);
    }

    private void fireBeforeUnderlyingCommit() {
      myEventSupport.getDispatcher().onBeforeUnderlyingCommit(myUnderlying);
    }

    private void collectAnnouncedRevisions() {
      if (!mymyEmpty && mymySuccess) {
        mymyAnnouncedRevisions = Collections15.hashSet();
        for (int i = 0; i < mymyChanging.length; i++) {
          RevisionCreator changer = mymyChanging[i];
          if (!changer.isEmpty()) {
            mymyAnnouncedRevisions.add(changer.asRevision());
          }
        }
        if (myNotificationForced != null)
          mymyAnnouncedRevisions.addAll(myNotificationForced);
      }
    }

    public void invoke(Atom[] atoms) {
      mymyCommittedAtoms = atoms;
    }

    private void doUnderlyingCommit() throws InterruptedException {
      if (!mymyEmpty) {
        mymyCommitLock = myTransactionController.beginCommit(TransactionImpl.this);
        synchronized (myTransactionController.getGlobalLock()) {
          Expansion.Result result = myUnderlying.commit(this);
          mymyDispatcher = myEventSupport.getDispatcherSnapshot();
          mymyOnCommitDispatcher = myEventSupport.getDispatcherSnapshot(mymyCommitLock);
          if (!result.isSuccessful()) {
            if (result.getVerificationException() != null) {
              throw new UnsafeCollisionException(result.getVerificationException());
            } else {
              Throwable exception = WorkspaceUtils.onFatalDatabaseProblem(result.getException());
              throw new Failure("transaction failed", exception);
            }
          }
          myCommitWCN = WCN.createWCN(myUnderlying.getCommitUCN());
          mymySuccess = true;
          synchronized (NOTIFICATIONS) {
            // put a placeholder into map, so other transactions that happen later, won't access in front
            // of us in notification order
            Runnable previous = getNotifications().put(myCommitWCN, null);
            assert previous == null;
          }
        }
      } else {
        mymyCommitLock = ProcessingLock.DUMMY;
        myUnderlying.rollback();
        mymyDispatcher = myEventSupport.getDispatcherSnapshot();
        mymyOnCommitDispatcher = mymyDispatcher;
        mymySuccess = false;
      }
    }

    private SortedMap<WCN, Runnable> getNotifications() {
      SortedMap<WCN, Runnable> notifications;
      PropertyMap map = myTransactionController.getTransactionServiceMap();
      synchronized (NOTIFICATIONS) {
        notifications = map.get(NOTIFICATIONS);
        if (notifications == null) {
          notifications = Collections15.treeMap();
          notifications = Collections.synchronizedSortedMap(notifications);
          map.put(NOTIFICATIONS, notifications);
        }
      }
      return notifications;
    }

    private boolean init() {
      synchronized (TransactionImpl.this) {
        if (!myState.commit(State.PENDING, State.COMMITTING))
          return false;
        mymyEmpty = isEmpty();
        int size = myChanging.size();
        RevisionCreatorFacade[] facades = new RevisionCreatorFacade[size];
        int i = 0;
        for (TLongObjectIterator ii = myChanging.iterator(); ii.hasNext();) {
          ii.advance();
          facades[i++] = (RevisionCreatorFacade) ii.value();
        }
        assert i == size;
        mymyChanging = facades;
        return true;
      }
    }
  }


  private static final class State {
    public static final State PENDING = new State("PENDING");
    public static final State COMMITTING = new State("COMMITTING");
    public static final State COMMITTED = new State("COMMITTED");
    public static final State ROLLED_BACK = new State("ROLLED_BACK");
    public static final State COMMIT_ERROR = new State("COMMIT_ERROR");

    private final String myName;

    public State(String name) {
      myName = name;
    }

    public String toString() {
      return myName;
    }
  }
}
