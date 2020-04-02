package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.TypedArtifact;
import com.almworks.api.database.util.WorkspaceUtils;
import com.almworks.api.install.Setup;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.universe.*;
import com.almworks.database.bitmap.BitmapIndexManager;
import com.almworks.database.objects.*;
import com.almworks.database.objects.remote.RCBArtifactImpl;
import com.almworks.database.schema.*;
import com.almworks.database.typed.TypedObjectFactory;
import com.almworks.util.cache2.LongKeyedLoader;
import com.almworks.util.cache2.NoCache;
import com.almworks.util.commons.Lazy;
import com.almworks.util.events.EventSource;
import com.almworks.util.exec.Context;
import com.almworks.util.threads.Threads;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

public class Basis implements SystemObjectResolver {
  public static final TypedKey<Class<? extends RuntimeException>> DATABASE_INCONSISTENCY_EXCEPTION = TypedKey.create("DATABASE_INCONSISTENCY_EXCEPTION");

  // use old version of value types
  public static final String INITPARAM_VALUETYPES_2 = "valuetypes.2";
  private static final String INITPARAM_READONLY = "readOnly";

  private static final int CACHE_CAPACITY = 10000;

  public final Universe ourUniverse;
  public final ValueFactory ourValueFactory;
  public final RevisionMonitor ourRevisionMonitor;
  public final Transformator ourTransformator;
  public final TransactionControlExt ourTransactionControl;
  public final AspectManager ourAspectManager = new AspectManagerImpl();
  public final BitmapCheckerStateImpl ourBitmapCheckerState = new BitmapCheckerStateImpl();

  public final SchemaValueTypes VALUETYPE;
  public final SchemaTypes TYPE;
  public final SchemaAttributes ATTRIBUTE;
  public final SystemSingletonCollection ourSingletons = new SystemSingletonCollection(this);
  public final ConsistencyWrapper ourConsistencyWrapper;

  private final NoCache<RevisionImpl> myRevisionCache;
  private final NoCache<AbstractArtifactImpl> myArtifactCache;
//  private final Cache<AtomKey, PhysicalRevisionChain> myRevisionChainCache;

  private Map<Class, TypedObjectFactory> myTypedFactoriesByClass = Collections15.hashMap();
  private Map<Long, TypedObjectFactory> myTypedFactoriesByItemKey = Collections15.hashMap();


  private final Resolver mySystemObjectsResolver;
  private final SystemObjectsCache mySystemObjectsCache;
  private final Map<Long, AbstractArtifactImpl> myEtherealArtifacts = Collections15.hashMap();
  private final boolean myReadOnly;

//  private static final int PROXY_TABLE_SIZE = 100000;
  private static final int KESH_B = 10;
  private static final int KESH_DIM_SIZE = 1 << KESH_B;
  private static final int KESH_MAX = 1 << (2 * KESH_B);
  private static final int KESH_LOW_MASK = KESH_DIM_SIZE - 1;

  private final ArtifactProxy[][] myArtifactProxyKesh = new ArtifactProxy[KESH_DIM_SIZE][];
  private final RevisionDecorator[][] myRevisionProxyKesh = new RevisionDecorator[KESH_DIM_SIZE][];

  private final Lazy<BitmapIndexManager> myIndexManager = new Lazy<BitmapIndexManager>() {
    public BitmapIndexManager instantiate() {
      return createBitmapIndexManager();
    }
  };
  protected final WorkArea myWorkArea;
  private static final boolean USE_CACHE = true;


  public Basis(Universe universe, ConsistencyWrapper consistency) {
    this(universe, consistency, null, null);
  }

  public Basis(Universe universe, ConsistencyWrapper consistency, WorkArea workArea) {
    this(universe, consistency, null, workArea);
  }

  public Basis(Universe universe, ConsistencyWrapper consistency, String[] features) {
    this(universe, consistency, features, null);
  }

  public Basis(Universe universe, ConsistencyWrapper consistency, String[] features,
    WorkArea workArea) {
    assert universe != null;
    assert consistency != null;
    ourUniverse = universe;
    ourConsistencyWrapper = consistency;

    universe.setCustomPropertiesIfCreating(
      Collections.singletonMap(Schema.SCHEMA_VERSION_PROPERTY_KEY, Schema.CURRENT_SCHEMA_VERSION));

    List<String> featureList = features != null ? Arrays.asList(features) : Collections15.<String>emptyList();

    mySystemObjectsResolver = new Resolver();
    mySystemObjectsCache = new SystemObjectsCache(mySystemObjectsResolver);
    ourValueFactory = featureList.contains(INITPARAM_VALUETYPES_2) ?
      (ValueFactory) new ValueFactoryImpl2(consistency, mySystemObjectsCache) :
      new ValueFactoryImpl(consistency, mySystemObjectsCache);

    myReadOnly = featureList.contains(INITPARAM_READONLY);

    if (USE_CACHE) {
      myRevisionCache = NoCache.create(new LongKeyedLoader<RevisionImpl>() {
        public RevisionImpl loadObject(long atomKey) {
          while (true) {
            try {
              return Basis.this.loadRevision(atomKey);
            } catch (DatabaseInconsistentException e) {
              throwInconsistencyException();
              ourConsistencyWrapper.handle(e, -1);
            }
          }
        }
      });

      myArtifactCache = NoCache.create(new LongKeyedLoader<AbstractArtifactImpl>() {
        public AbstractArtifactImpl loadObject(long key) {
          while (true) {
            try {
              return Basis.this.loadArtifact(key);
            } catch (DatabaseInconsistentException e) {
              throwInconsistencyException();
              ourConsistencyWrapper.handle(e, -1);
            }
          }
        }
      });
    } else {
      myRevisionCache = null;
      myArtifactCache = null;
      assert false;
    }

    runStats();

    ourSingletons.chain(TYPE = new SchemaTypes(this));
    ourSingletons.chain(VALUETYPE = new SchemaValueTypes(this));
    ourSingletons.chain(ATTRIBUTE = new SchemaAttributes(this));

    ourTransactionControl = new TransactionControlImpl(this);
    ourRevisionMonitor = new RevisionMonitor(this);  //kludge
    ourTransformator = new Transformator(this); // kludge

    myWorkArea = workArea;
  }

  private static void throwInconsistencyException() {
    Class<? extends RuntimeException> eClass = Context.get(DATABASE_INCONSISTENCY_EXCEPTION);
    if (eClass != null) {
      try {
        throw eClass.newInstance();
      } catch (InstantiationException e1) {
        Log.warn(e1);
        // ignore
      } catch (IllegalAccessException e1) {
        Log.warn(e1);
        // ignore
      }
    }
  }

  protected BitmapIndexManager createBitmapIndexManager() {
    return new BitmapIndexManager(this, myWorkArea);
  }

  public void start() {
    ourTransactionControl.setLateWCN(WCN.createWCN(getUnderlyingUCN()));
    getBitmapIndexManager().start();
  }

  public BitmapIndexManager getBitmapIndexManager() {
    return myIndexManager.get();
  }

  public Object defaultTransactionContext() {
    return this;
  }

  public long getAtomID(Revision revision) {
    return revision.getKey();
  }

  public long getAtomID(RevisionChain chain) {
    return chain.getKey();
  }

  public long getAtomID(@NotNull Artifact object) {
    return object.getKey();
  }

  // Returns the WCN that has been processed by (level one) event handlers.
  // This WCN gets updated only after low-level notifications including onNewRevisionsAppeared() have worked.
  // When TransactionListener.onCommit() is invoked, this value is already promoted.
  // This method should be used for caching database revisions.
  public WCN getCurrentWCN() {
    WCN wcn = ourTransactionControl.getLateWCN();
    assert getUnderlyingUCN() >= wcn.getUCN();
    return wcn;
  }

  // Returns the WCN that is updated under the lock in the universe.
  // The early WCN is promoted before all transaction notifications are executed.
  public long getUnderlyingUCN() {
    return ourUniverse.getUCN();
  }

  public WCN.Range getFuture() {
    return WCN.createRange(getCurrentWCN().getUCN(), Universe.END_OF_THE_UNIVERSE);
  }

  /**
   * mostly unsynchronized *
   */
  public ArtifactProxy getArtifact(long key) {
    if (key >= KESH_MAX || key < 0)
      return new ArtifactProxy(key, this);
    int k = (int)key;
    int kX = k >> KESH_B;
    int kS = k & KESH_LOW_MASK;
    ArtifactProxy[] subkesh = myArtifactProxyKesh[kX];
    if (subkesh == null) {
      synchronized(myArtifactProxyKesh) {
        subkesh = myArtifactProxyKesh[kX];
        if (subkesh == null) {
          subkesh = new ArtifactProxy[KESH_DIM_SIZE];
          myArtifactProxyKesh[kX] = subkesh;
        }
      }
    }
    ArtifactProxy proxy = subkesh[kS];
    if (proxy == null) {
      // here we don't care about synchronization
      proxy = new ArtifactProxy(key, this);
      subkesh[kS] = proxy;
    }
    return proxy;
  }

  public Address getObjectAddress(long atomID) {
    return new Address("tracker:todo:" + atomID);
  }

  public WCN.Range getPast() {
    return WCN.createRange(Universe.BIG_BANG, getCurrentWCN().getUCN());
  }

  /**
   * Unsynchronized!
   */
  public RevisionWithInternals getRevision(long key, RevisionIterator revisionIterator) {
    if (key >= KESH_MAX || key < 0)
      return createRevisionDecorator(key, revisionIterator);
    int k = (int)key;
    int kX = k >> KESH_B;
    int kS = k & KESH_LOW_MASK;
    RevisionDecorator[] subkesh = myRevisionProxyKesh[kX];
    if (subkesh == null) {
      synchronized(myRevisionProxyKesh) {
        subkesh = myRevisionProxyKesh[kX];
        if (subkesh == null) {
          subkesh = new RevisionDecorator[KESH_DIM_SIZE];
          myRevisionProxyKesh[kX] = subkesh;
        }
      }
    }
    RevisionDecorator decorator = subkesh[kS];
    if (decorator == null || decorator.getRevisionIterator() != revisionIterator) {
      decorator = createRevisionDecorator(key, revisionIterator);
      subkesh[kS] = decorator;
    }
    return decorator;
  }

  private RevisionDecorator createRevisionDecorator(long key, RevisionIterator revisionIterator) {
    return new RevisionDecorator(this, new RevisionProxy(key, this, revisionIterator));
  }

  // #1600 optimize
  public RevisionWithInternals getRevisionOrNull(long atomID, RevisionAccess strategy) {
    Artifact artifact = null;
    try {
      artifact = doGetArtifactByRevision(atomID);
    } catch (DatabaseInconsistentException e) {
      return null;
    }
    if (!artifact.isAccessStrategySupported(strategy))
      return null;
    RevisionChain chain = artifact.getChain(strategy);
    RevisionWithInternals revision = getRevision(atomID, ((AbstractRevisionChain) chain).getRevisionIterator());
    return chain.containsRevision(revision) ? revision : null;
  }


  public RevisionChain getPhysicalChain(long key) {
    return new PhysicalRevisionChain(this, key);
  }

  public RevisionWithInternals getRevisionProxy(long atomID, RevisionIterator revisionIterator) {
    return getRevision(atomID, revisionIterator);
  }

  public <T extends TypedArtifact> T getSystemObject(TypedKey<T> systemObject) {
    return mySystemObjectsCache.getSystemObject(systemObject);
  }

  public TypedArtifact getTypedObject(final Artifact artifact) {
    Threads.assertLongOperationsAllowed();
    while (true) {
      try {
        Value value = artifact.getFirstRevision().getValue(ATTRIBUTE.type);
        if (value == null)
          return null;
        Artifact typeObject = value.getValue(Artifact.class);
        if (typeObject == null)
          throw new DatabaseInconsistentException("artifact " + artifact + " has type " + value);
        TypedObjectFactory factory = null;

        Artifact t = typeObject;
        do {
          factory = getTypedFactory(t);
          if (factory != null)
            break;
          t = t.getLastRevision().getValue(ATTRIBUTE.superType, Artifact.class);
        } while (t != null && !WorkspaceUtils.equals(t, TYPE.generic));

        if (factory == null) {
          throw new DatabaseInconsistentException("no type factory for type " + typeObject);
        } else {
          TypedArtifact typed = factory.loadTyped(Basis.this, artifact);
          return typed;
        }

      } catch (DatabaseInconsistentException e) {
        throwInconsistencyException();
        ourConsistencyWrapper.handle(e, -1);
      }
    }
  }

  private TypedObjectFactory getTypedFactory(Artifact typeObject) {
    TypedObjectFactory factory;
    synchronized (myTypedFactoriesByItemKey) {
      long key = typeObject.getKey();
      factory = myTypedFactoriesByItemKey.get(key);
      if (factory == null) {
        synchronized (myTypedFactoriesByClass) {
          for (Iterator<TypedObjectFactory> it = myTypedFactoriesByClass.values().iterator(); it.hasNext();) {
            TypedObjectFactory f = it.next();
            if (key == f.getTypeArtifact(Basis.this).getPointerKey()) {
              factory = f;
              break;
            }
          }
        }
        if (factory != null) {
          myTypedFactoriesByItemKey.put(key, factory);
        }
      }
    }
    return factory;
  }

  public <T extends TypedArtifact> TypedObjectFactory<T> getTypedObjectFactory(Class<T> typedClass) {
    synchronized (myTypedFactoriesByClass) {
      return myTypedFactoriesByClass.get(typedClass);
    }
  }

  public <T extends TypedArtifact> void installTypedObjectFactory(TypedObjectFactory<T> factory) {
    synchronized (myTypedFactoriesByClass) {
      myTypedFactoriesByClass.put(factory.getTypedClass(), factory);
    }
    // cannot install here into myTypedFactoriesByItemKey!
    // artifacts may not be initialized - install lazily later
  }

  public boolean isRevisionAtom(Atom atom) {
    if (atom == null)
      return false;
    Threads.assertLongOperationsAllowed();
    // todo atom marker?
    long marker = atom.getLong(Schema.KL_ATOM_MARKER);
    if (marker == -1)
      return false;
    if (marker == Schema.ATOM_REVISION.getLong())
      return true;
    if (marker == Schema.ATOM_LOCAL_ARTIFACT.getLong())
      return true;
    if (marker == Schema.ATOM_CHAIN_HEAD.getLong())
      return true;
    return false;
  }

  public Index getIndex(final String indexName) {
    Threads.assertLongOperationsAllowed();
    while (true) {
      try {
        Index index = ourUniverse.getIndex(indexName);
        if (index == null)
          throw new DatabaseInconsistentException("no index " + indexName);
        return index;
      } catch (DatabaseInconsistentException e) {
        throwInconsistencyException();
        ourConsistencyWrapper.handle(e, -1);
      }
    }
  }

  Atom getAtom(long atomId) throws DatabaseInconsistentException {
    Atom atom = ourUniverse.getAtom(atomId);
    if (atom == null)
      throw new DatabaseInconsistentException("no atom for [" + atomId + "]");
    return atom;
  }

  private long getAtomMarker(Atom atom) {
    long marker = Schema.KL_ATOM_MARKER.get(atom);
    return marker;
  }

  private AbstractArtifactImpl loadArtifact(long atomKey) throws DatabaseInconsistentException {
    Threads.assertLongOperationsAllowed();
    synchronized (myEtherealArtifacts) {
      AbstractArtifactImpl artifact = myEtherealArtifacts.get(atomKey);
      if (artifact != null)
        return artifact;
    }

    Atom atom = getAtom(atomKey);
    long marker = getAtomMarker(atom);
    if (Schema.ATOM_LOCAL_ARTIFACT.equals(marker)) {
      return new LocalArtifact(this, atomKey);
    } else if (Schema.ATOM_RCB_ARTIFACT.equals(marker)) {
      return new RCBArtifactImpl(this, atomKey);
    } else if (Schema.ATOM_CHAIN_HEAD.equals(marker) && atom.getLong(Schema.KL_REINCARNATION) >= 0) {
      // support reincarnations
      return new LocalArtifact(this, atomKey);
    } else {
      if (Setup.compatibilityRequired(Setup.COMPATIBLE_0_3)) {
        // versions up to 0.4 didn't have markers on artifact/revision/chain atoms.
        return new LocalArtifact(this, atomKey);
      } else {
        throw new BadMarkerException(atomKey, marker);
      }
    }
  }

  public void storeEtherealArtifact(AbstractArtifactImpl impl, EventSource<TransactionListener> eventSource) {
    synchronized (myEtherealArtifacts) {
      final long key = impl.key();
      assert !myEtherealArtifacts.containsKey(key);
      myEtherealArtifacts.put(key, impl);
      eventSource.addStraightListener(new TransactionListener.Adapter() {
        public void onAfterUnderlyingCommit(Expansion underlying, boolean success) {
          AbstractArtifactImpl artifact = myEtherealArtifacts.remove(key);
          assert artifact != null;
          myArtifactCache.invalidate(key);
        }
      });
    }
  }

  public boolean isArtifactAtom(Atom atom) {
    return isRCBArtifactAtom(atom) != null;
  }


  private RevisionImpl loadRevision(long atomKey) throws DatabaseInconsistentException {
    Threads.assertLongOperationsAllowed();
    Atom atom = getAtom(atomKey);
    long marker = getAtomMarker(atom);
    if (!Schema.ATOM_REVISION.equals(marker) && !Schema.ATOM_LOCAL_ARTIFACT.equals(marker) &&
      !Schema.ATOM_CHAIN_HEAD.equals(marker)) {

      if (!Setup.compatibilityRequired(Setup.COMPATIBLE_0_3))
        throw new BadMarkerException(atomKey, marker);
    }
    return new RevisionImpl(this, atomKey, atom);
  }

  private void runStats() {
    if (USE_CACHE) {
/*
      ((LRUCache2) myArtifactCache).runStats("artifacts", 10000);
      ((LRUCache2) myRevisionCache).runStats("revisions", 10000);
*/
    }
/*
    if (Env.isRunFromIDE()) {
      ((LRUCache2)myArtifactCache).runStats("artifacts", 10000);
      ((LRUCache2)myRevisionCache).runStats("revisions", 10000);
    }
*/
  }

  public Artifact getArtifactByChainKey(final long key) {
    Threads.assertLongOperationsAllowed();
    while (true) {
      try {
        return doGetArtifactByChainKey(key);
      } catch (DatabaseInconsistentException e) {
        throwInconsistencyException();
        ourConsistencyWrapper.handle(e, -1);
      }
    }
  }

  private Artifact doGetArtifactByChainKey(final long key) throws DatabaseInconsistentException {
    Atom atom = getAtom(key);
    long marker = getAtomMarker(atom);
    if (Schema.ATOM_LOCAL_ARTIFACT.equals(marker) || Schema.ATOM_RCB_ARTIFACT.equals(marker)) {
      return getArtifact(key);
    } else if (!Schema.ATOM_CHAIN_HEAD.equals(marker)) {
      if (Setup.compatibilityRequired(Setup.COMPATIBLE_0_3)) {
        if (marker == -1 && atom.getLong(Schema.KA_CHAIN_HEAD) == atom.getAtomID())
          return getArtifact(key);
        else
          throw new DatabaseInconsistentException("database inconsistent [" + atom + "]");
      } else {
        throw new BadMarkerException(key, marker);
      }
    }
    long ptr = atom.getLong(Schema.KA_CHAIN_ARTIFACT);
    if (ptr < 0)
      throw new DatabaseInconsistentException("bad chain header: pointer to artifact " + ptr);
    return getArtifact(ptr);
  }

  public Artifact getArtifactByRevision(long atomID) {
    return getArtifactByRevision(atomID, ourConsistencyWrapper);
  }

  public Artifact getArtifactByRevision(final long atomID, ConsistencyWrapper consistencyWrapper) {
    Threads.assertLongOperationsAllowed();
    while (true) {
      try {
        return doGetArtifactByRevision(atomID);
      } catch (DatabaseInconsistentException e) {
        consistencyWrapper.handle(e, -1);
      }
    }
  }

  private Artifact doGetArtifactByRevision(final long atomID) throws DatabaseInconsistentException {
    Atom atom = getAtom(atomID);
    if (!isRevisionAtom(atom))
      throw new DatabaseInconsistentException("database inconsistent [" + atom + "]");
    long chainID = atom.getLong(Schema.KA_CHAIN_HEAD);
    if (chainID < 0)
      throw new DatabaseInconsistentException("no chain for " + atom);
    return doGetArtifactByChainKey(chainID);
  }

  public NoCache<AbstractArtifactImpl> getArtifactsCache() {
    return myArtifactCache;
  }

  public NoCache<RevisionImpl> getRevisionCache() {
    return myRevisionCache;
  }

  /**
   * Returns TRUE if atom is artifact atom for RCB artifact, FALSE if it is local artifact, and null if it is
   * not an artifact.
   */
  public Boolean isRCBArtifactAtom(long atomID) {
    Atom atom = ourUniverse.getAtom(atomID);
    return isRCBArtifactAtom(atom);
  }

  private Boolean isRCBArtifactAtom(Atom atom) {
    if (atom == null)
      return null;
    long marker = getAtomMarker(atom);
    if (Schema.ATOM_LOCAL_ARTIFACT.equals(marker))
      return Boolean.FALSE;
    if (Schema.ATOM_RCB_ARTIFACT.equals(marker))
      return Boolean.TRUE;
    if (Setup.compatibilityRequired(Setup.COMPATIBLE_0_3) && Schema.ATOM_CHAIN_HEAD.equals(marker)) {
      if (Schema.KA_CHAIN_ARTIFACT.get(atom) >= 0) {
        // main chain in RCB
        return null;
      }
      return Boolean.FALSE;
    }
    return null;
  }

  public static class BadMarkerException extends DatabaseInconsistentException {
    public BadMarkerException(long atomKey, long marker) {
      super("bad marker for [" + atomKey + "]: " + marker);
    }
  }


  private class Resolver implements SystemObjectResolver {
    public <T extends TypedArtifact> T getSystemObject(TypedKey<T> artifactKey) {
      Threads.assertLongOperationsAllowed();
      SystemSingleton<T> singleton = ourSingletons.search(artifactKey);
      if (singleton == null)
        return null;
      TypedArtifact typedObject = getTypedObject(singleton.getArtifact());
      return (T) typedObject;
    }
  }

  public boolean isReadOnly() {
    return myReadOnly;
  }
}
