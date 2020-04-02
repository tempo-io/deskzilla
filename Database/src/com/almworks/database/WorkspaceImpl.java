package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.TypedArtifact;
import com.almworks.api.exec.ApplicationManager;
import com.almworks.api.inquiry.Inquiries;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.universe.Universe;
import com.almworks.database.bitmap.BitmapIndexManager;
import com.almworks.database.filter.FilterManagerImpl;
import com.almworks.database.typed.*;
import com.almworks.util.Pair;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.progress.Progress;
import com.almworks.util.threads.CanBlock;
import com.almworks.util.threads.Threads;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.Lifespan;

import java.util.Iterator;
import java.util.Set;

/**
 * :todoc:
 *
 * @author sereda
 */
public class WorkspaceImpl implements Workspace {
  private final Basis myBasis;
  private final FilterManager myFilterManager;
  private boolean myOpen = false;
  private SystemViews mySystemViews;

  public WorkspaceImpl(Universe universe, Inquiries inquires,
    ApplicationManager applicationManager, WorkArea workArea) {
    NormalConsistencyWrapper consistency = new NormalConsistencyWrapper(inquires, applicationManager);
    myBasis = new Basis(universe, consistency, workArea);
    myFilterManager = new FilterManagerImpl(myBasis, myBasis);
    mySystemViews = new SystemViewsImpl(myBasis, myFilterManager);
  }

  public WorkspaceImpl(Basis basis) {
    myBasis = basis;
    myFilterManager = new FilterManagerImpl(myBasis, myBasis);
    mySystemViews = new SystemViewsImpl(basis, myFilterManager);
  }

  public WCN getCurrentWCN() {
    checkOpen();
    return myBasis.getCurrentWCN();
  }

  public long getUnderlyingUCN() {
    checkOpen();
    return myBasis.getUnderlyingUCN();
  }

  public void start() {
    myBasis.start();
    migrate();
    repair();
    open();
  }

  private void migrate() {
/*
    Universe universe = myBasis.ourUniverse;
    if (!(universe instanceof FileUniverse))
      return;
    ((Startable)universe).stop();

    MigrationControllerImpl controller = new MigrationControllerImpl();
    ValueMarshallingCompactizationMigration migration = new ValueMarshallingCompactizationMigration(myWorkArea, controller);
    try {
      migration.migrate();
    } catch (MigrationFailure migrationFailure) {
      throw new Failure(migrationFailure);
    }

    ((Startable)universe).start();
*/
  }

  public void stop() {
    close();
    WorkspaceStatic.cleanup();
  }

  public synchronized void open() {
    Threads.assertLongOperationsAllowed();
    if (myOpen)
      return;
    Transaction transaction = myBasis.ourTransactionControl.beginTransaction();
    myBasis.ourSingletons.initializeStepOne(this, transaction);
    myBasis.VALUETYPE.installDefaultValueTypes(myBasis.ourValueFactory);
    myBasis.ourSingletons.initializeStepTwo();
    transaction.commitUnsafe();
    installTypedFactories();
    loadSystemObjects();
    getAspectManager().registerAspectProvider(ArtifactPointer.class, new SchemaAspects());
    setupStatic();
    myOpen = true;
  }

  // kludge
  // loading system objects into static context to avoid dragging workspace along every class
  @CanBlock
  private void setupStatic() {
    Set<TypedKey<? extends TypedArtifact>> keys = getSystemObjectKeys();
    WorkspaceStatic.load(this, (Set)keys);
  }

  public synchronized void repair() {
    Threads.assertLongOperationsAllowed();
    WorkspaceMaintenance.repair(myBasis.ourUniverse);
  }

  public void close() {
    // todo
  }

  public TransactionControl getTransactionControl() {
    checkOpen();
    return myBasis.ourTransactionControl;
  }

  public Transaction beginTransaction() {
    checkOpen();
    return myBasis.ourTransactionControl.beginTransaction();
  }

  public Pair<Detach,WCN> addListener(TransactionListener listener) {
    checkOpen();
    return myBasis.ourTransactionControl.addListener(listener);
  }

  public WCN addListener(Lifespan lifespan, TransactionListener listener) {
    checkOpen();
    return myBasis.ourTransactionControl.addListener(lifespan, listener);
  }

  public Pair<Detach,WCN> addListener(ThreadGate gate, TransactionListener listener) {
    checkOpen();
    return myBasis.ourTransactionControl.addListener(gate, listener);
  }

  public WCN addListener(Lifespan lifespan, ThreadGate gate, TransactionListener listener) {
    checkOpen();
    return myBasis.ourTransactionControl.addListener(lifespan, gate, listener);
  }

  public void removeListener(TransactionListener listener) {
    myBasis.ourTransactionControl.removeListener(listener);
  }

  public SystemViews getViews() {
    return mySystemViews;
  }

  public FilterManager getFilterManager() {
    // checkOpen(); - todo do we need it here?
    return myFilterManager;
  }

  public AspectManager getAspectManager() {
    return myBasis.ourAspectManager;
  }

  public <T extends TypedArtifact> T getSystemObject(TypedKey<T> artifactKey) {
    return myBasis.getSystemObject(artifactKey);
  }

  private void loadSystemObjects() {
    SystemObjects.Loader.loadConstants();
    Set<TypedKey<? extends TypedArtifact>> keys = getSystemObjectKeys();
    for (Iterator<TypedKey<? extends TypedArtifact>> ii = keys.iterator(); ii.hasNext();) {
      myBasis.getSystemObject(ii.next());
    }
  }

  private Set<TypedKey<? extends TypedArtifact>> getSystemObjectKeys() {
    Set<TypedKey<? extends TypedArtifact>> keys = Collections15.hashSet();
    keys.addAll(SystemObjects.Reg.attributeRegistry.getRegisteredKeys());
    keys.addAll(SystemObjects.Reg.valuetypeRegistry.getRegisteredKeys());
    keys.addAll(SystemObjects.Reg.typeRegistry.getRegisteredKeys());
    return keys;
  }

  private void checkOpen() {
    if (!myOpen)
      throw new IllegalStateException();
  }

  private synchronized void installTypedFactories() {
    myBasis.installTypedObjectFactory(new AttributeImpl.Factory(myBasis));
    myBasis.installTypedObjectFactory(new ValueTypeDescriptorImpl.Factory(myBasis));
    myBasis.installTypedObjectFactory(new ArtifactTypeImpl.Factory(myBasis));
  }

  public Universe getUniverse() {
    checkOpen();
    return myBasis.ourUniverse;
  }

  public BitmapIndexManager getIndexManager() {
    return myBasis.getBitmapIndexManager();
  }

  public Artifact getArtifactByKey(long key) throws InvalidItemKeyException {
    Boolean r = myBasis.isRCBArtifactAtom(key);
    if (r == null)
      throw new InvalidItemKeyException("::" + key);
    return myBasis.getArtifact(key);
  }

  public void dropIndexes(Progress progress) throws InterruptedException {
    Threads.assertLongOperationsAllowed();
    myBasis.getBitmapIndexManager().dropAllIndexes(progress);
  }

  public IndexCheckerState getIndexCheckerState() {
    return myBasis.ourBitmapCheckerState;
  }
}
