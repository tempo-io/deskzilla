package com.almworks.spi.provider;

import com.almworks.api.application.Attachment;
import com.almworks.api.application.LoadedItem;
import com.almworks.api.application.order.Order;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.constraint.Constraint;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.container.MutableComponentContainer;
import com.almworks.api.engine.*;
import com.almworks.integers.LongIterator;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.sync.*;
import com.almworks.items.util.BadUtil;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.MapListModel;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.commons.*;
import com.almworks.util.config.Configuration;
import com.almworks.util.events.EventSource;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.*;
import com.almworks.util.models.TableColumnAccessor;
import com.almworks.util.threads.*;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.*;
import org.almworks.util.detach.DetachComposite;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;
import org.picocontainer.Startable;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;

import static com.almworks.util.model.SetHolderUtils.actualizingSequentialListener;

public abstract class AbstractConnection<P extends ItemProvider, C extends BaseConnectionContext>
  implements Connection, Startable
{
  public static final String RECENTS = "recents";
  private static final String AUTO_SYNC_SUBSET = "autoSync";
  private static final String AUTO_SYNC_MODE_SETTING = "mode";

  protected final P myProvider;
  protected final String myConnectionID;
  protected final MutableComponentContainer myContainer;
  private final Configuration myConnectionConfig;
  private C myContext;
  protected final SetHolderModel<SyncTask> mySyncTasksModel = new SetHolderModel<SyncTask>();
  private boolean myMaterialized = false;

  private final BasicScalarModel<AutoSyncMode> myAutoSyncMode = BasicScalarModel.create(true, null);

  private final AtomicReference<MapListModel<ConstraintDescriptor, String>> myDescriptorsModel = new AtomicReference<MapListModel<ConstraintDescriptor, String>>();

  protected AbstractConnection(P provider, MutableComponentContainer container, String connectionID) {
    myProvider = provider;
    myContainer = container;
    myConnectionID = connectionID;
    Configuration connectionConfig = provider.getConnectionConfig(connectionID);
    myConnectionConfig = connectionConfig != null ? connectionConfig : Configuration.EMPTY_CONFIGURATION;
  }

  protected void initContext(C context) {
    assert myContext == null;
    assert context != null;
    myContext = context;
  }

  public String getConnectionID() {
    return myConnectionID;
  }

  public ItemProvider getProvider() {
    return myProvider;
  }

  @Nullable
  public ScalarModel<Collection<RemoteQuery2>> getRemoteQueries2() {
    return null;
  }

  // todo remove this method - but provide a way to connect bugzilla gui classes to bugzilla provider
  public ComponentContainer getConnectionContainer() {
    return myContainer;
  }

  public synchronized void start() {
    if (!myContext.commitState(ConnectionState.INITIAL, ConnectionState.STARTING)) {
      if (myContext.commitState(ConnectionState.STOPPED, ConnectionState.STARTING)) {
        Log.debug("starting stopped connection " + this);
      } else {
        return;
      }
    }
    ThreadGate.LONG(this).execute(new Runnable() {
      public void run() {
        boolean success = false;
        try {
          longStart();
          success = true;
        } catch (Exception e) {
          Log.warn("connection startup was unsuccessful", e);
        } finally {
          myContext.commitState(ConnectionState.STARTING, success ? ConnectionState.READY : ConnectionState.STOPPED);
        }
      }
    });
  }

  protected void longStart() throws InterruptedException {
    if (!myProvider.isEnabled()) {
      return;
    }
    try {
      myProvider.getState().waitValue(Condition.isEqual(ItemProviderState.STARTED));
      materialize();
      getContext().start();
    } catch (ProviderDisabledException e) {
      Log.warn(e);
      // ignore
    }
  }

  public void startConnection() {
    myContainer.start();
    mySyncTasksModel.add(getConnectionSynchronizer());
  }

  public C getContext() {
    assert myContext != null;
    return myContext;
  }

  public ScalarModel<ConnectionState> getState() {
    return getContext().getState();
  }

  public ConnectionSynchronizer getConnectionSynchronizer() {
    ConnectionSynchronizer result = myContainer.getActor(ConnectionSynchronizer.ROLE);
    assert result != null;
    return result;
  }

  public SetHolder<SyncTask> getSyncTasks() {
    return mySyncTasksModel;
  }

  public QueryUrlInfo getQueryURL(Constraint constraint, DBReader reader) throws InterruptedException {
    return null;
  }

  @Override
  @CanBlock
  public String getItemUrl(ItemVersion localVersion) {
    return "tracker:local/" + localVersion.getItem();
  }

  @ThreadAWT
  @NotNull
  public Pair<DBFilter, Constraint> getViewAndConstraintForUrl(String url) throws CantPerformExceptionExplained {
    throw new CantPerformExceptionExplained("not supported");
  }

  public boolean isItemUrl(String itemUrl) {
    return false;
  }

  @Override
  public String getItemIdForUrl(String itemUrl) {
    return null;
  }

  @Deprecated
  @CanBlock
  @Nullable
  public String getItemId(ItemVersion localVersion) {
    return null;
  }

  @Deprecated
  @CanBlock
  @Nullable
  public String getItemSummary(ItemVersion version) {
    return null;
  }

  public boolean hasCapability(Capability capability) {
    return false;
  }

  @NotNull
  @Override
  public Comparator<? super TableColumnAccessor<LoadedItem, ?>> getMainColumnsOrder() {
    return TableColumnAccessor.NAME_ORDER;
  }

  @Override
  public AListModel<? extends TableColumnAccessor<LoadedItem, ?>> getAuxiliaryColumns() {
    return null;
  }

  @Override
  public Comparator<? super TableColumnAccessor<LoadedItem, ?>> getAuxColumnsOrder() {
    return null;
  }

  @ThreadAWT
  public AListModel<? extends Order> getOrdersModel() {
    //noinspection unchecked
    return AListModel.EMPTY;
  }


  public ScalarModel<InitializationState> getInitializationState() {
    return getContext().getInitializationState();
  }

  public Configuration getConnectionConfig(String ... subsets) {
    Configuration config = myConnectionConfig;
    for (String subset : subsets)
      config = config.getOrCreateSubset(subset);
    return config;
  }

  protected void materialize() {
    if(myMaterialized) {
      return;
    }

    myMaterialized = true;

    Database.require().writeForeground(new WriteTransaction<Void>() {
      @Override
      public Void transaction(DBWriter writer) throws DBOperationCancelledException {
        doMaterialize(writer);
        return null;
      }
    }).waitForCompletion();
  }

  protected abstract void doMaterialize(DBWriter writer);

  protected boolean isMaterialized() {
    return myMaterialized;
  }

  public synchronized void stop() {
    if (!getContext().commitState(ConnectionState.READY, ConnectionState.STOPPING)) {
      assert false : this + " is not started";
      return;
    }
    ThreadGate.LONG(new Object()).execute(new Runnable() {
      public void run() {
        try {
          ConnectionSynchronizer synchronizer = myContainer.getActor(ConnectionSynchronizer.ROLE);
          assert synchronizer != null;
          if (synchronizer.isCancellableState()) {
            synchronizer.cancel();
            if (synchronizer instanceof Startable)
              ((Startable) synchronizer).stop();
          }
          getContext().stop();
        } finally {
          getContext().commitState(ConnectionState.STOPPING, ConnectionState.STOPPED);
        }
      }
    });
  }

  public void requestReinitialization() {
    getContext().requestReinitialization();
    InitializationState state = getContext().getInitializationState().getValue();
    if (state == null || !state.isInitializationRequired())
      return;
    AbstractConnectionInitializer initializer = myContainer.getActor(AbstractConnectionInitializer.ROLE);
    if (initializer == null) {
      assert false : this;
      Log.warn("cannot get initializer");
      return;
    }
    initializer.requestInitializationNow();
  }

  public void stopConnection() {
    mySyncTasksModel.remove(getConnectionSynchronizer());
    myContainer.stop();
  }

  public synchronized void removeConnection(final Runnable onConnectionItemsRemoved) {
    // todo what if STARTING?
    if (getContext().getState().getValue() != ConnectionState.READY)
      stopConnection();
    ThreadGate.LONG(this).execute(new Runnable() {
      public void run() {
        try {
          final C context = getContext();
          context.getState().waitValue(Condition.isEqual(ConnectionState.STOPPED));
          removeAllItems().onSuccess(ThreadGate.AWT, new Procedure() {
            @Override
            public void invoke(Object arg) {
              context.commitState(ConnectionState.STOPPED, ConnectionState.REMOVED);
              context.clearSyncRegistry();
              myConnectionConfig.removeMe();
              onConnectionItemsRemoved.run();
            }
          });
        } catch (InterruptedException e) {
          throw new RuntimeInterruptedException(e);
        }
      }
    });
  }

  /**
   * Removes all items and closes all editors.
   */
  private DBResult removeAllItems() {
    final long connectionItem = getConnectionItem();
    final BoolExpr<DP> connectionFilter = DPEquals.create(SyncAttributes.CONNECTION, connectionItem);
    final SyncManager syncMan = myContainer.getActor(SyncManager.ROLE);
    return Database.require().writeBackground(new WriteTransaction() {
      public Object transaction(final DBWriter writer) {
        writer.query(connectionFilter).fold(null, new LongObjFunction2() {
          public Object invoke(long item, Object _) {
            clearItemAndSlaves(item, writer, syncMan);
            return null;
          }
        });
        clearItemAndSlaves(connectionItem, writer, null);
        return null;
      }
    });
  }

  private static void clearItemAndSlaves(long item, DBWriter writer, @Nullable SyncManager syncMan) {
    for (LongIterator i = BadUtil.getSlaves(writer, item).iterator(); i.hasNext();) {
      clearItemCloseEditor(i.next(), writer, syncMan);
    }
    clearItemCloseEditor(item, writer, syncMan);
  }

  private static void clearItemCloseEditor(long item, DBWriter writer, @Nullable SyncManager syncMan) {
    writer.clearItem(item);
    if (syncMan != null) {
      EditorLock lock = syncMan.findLock(item);
      if (lock != null) {
        lock.release();
      }
    }
  }

  public String toString() {
    return "connection " + getName();
  }

  public String getName() {
    return getConfiguration().getSetting(CommonConfigurationConstants.CONNECTION_NAME, "");
  }

  public final void subscribeToTaskUntilFinalState(final SyncTask task) {
    final DetachComposite subscriptionLife = new DetachComposite();
    task.addChangeListener(subscriptionLife, ThreadGate.STRAIGHT, new ChangeListener() { @Override public void onChange() {
      if (task.getState().getValue().isFinal()) subscriptionLife.detach();
    }});
    subscribeToTask(subscriptionLife, task);
  }

  protected final void subscribeToTask(Lifespan life, final SyncTask task) {
    // we have to use queued gate here to avoid assertings thrown from BasicValueModel when SyncWindow will subscribe to it while setValue() event is dispatched
    EventSource<ScalarModel.Consumer<SyncTask.State>> stateSource = task.getState().getEventSource();
    stateSource.addListener(life, ThreadGate.LONG_QUEUED(stateSource), new ScalarModel.Adapter<SyncTask.State>() {
      public void onScalarChanged(ScalarModelEvent<SyncTask.State> event) {
        updateSyncTasks(task, !task.getProblems().isEmpty());
      }
    });
    task.getProblems().addInitListener(life, ThreadGate.LONG_QUEUED(task), actualizingSequentialListener(new SetHolder.Listener<SyncProblem>() {
      int problemCount = 0;
      @Override
      public void onSetChanged(@NotNull SetHolder.Event<SyncProblem> event) {
        problemCount += event.getAdded().size() - event.getRemoved().size();
        assert problemCount >= 0;
        updateSyncTasks(task, problemCount > 0);
      }
    }));
  }

  private void updateSyncTasks(SyncTask task, boolean hasProblems) {
    boolean keep = SyncTask.State.isWorking(task.getState().getValue()) || hasProblems;
    if (keep) {
      mySyncTasksModel.add(task);
    } else {
      mySyncTasksModel.remove(task);
    }
  }

  @CanBlock
  @NotNull
  public Collection<? extends Attachment> getItemAttachments(ItemVersion primaryItem) {
    return Collections15.emptyCollection();
  }

  public CollectionModel<RemoteQuery> getRemoteQueries() {
    return null;
  }

  public boolean isAllowedByLicense() {
    return true;
  }

  @NotNull
  @Override
  public final AListModel<? extends ConstraintDescriptor> getDescriptors() {
    MapListModel<ConstraintDescriptor, String> model;
    while ((model = myDescriptorsModel.get()) == null)
      myDescriptorsModel.compareAndSet(null, MapListModel.create(createDescriptorsModel(), ConstraintDescriptor.TO_ID));
    return model;
  }

  @NotNull
  protected abstract AListModel<? extends ConstraintDescriptor> createDescriptorsModel();

  @Nullable
  @ThreadSafe
  public ConstraintDescriptor getDescriptorByIdSafe(String id) {
    MapListModel<ConstraintDescriptor, String> model = myDescriptorsModel.get();
    return model != null ? model.get(id) : null;
  }

  protected String getExternalIdSummaryStringPrefixed(ItemVersion version, String prefix) {
    String id = getItemId(version);
    String summary = getItemSummary(version);
    boolean hasId = id != null && id.length() > 0;
    boolean hasSummary = summary != null && summary.length() > 0;
    if (hasId) {
      if (hasSummary) {
        return prefix + id + " " + summary;
      } else {
        return prefix + id;
      }
    } else {
      if (hasSummary)
        return summary;
      else
        return null;
    }
  }

  @Nullable
  @CanBlock
  public String getExternalIdSummaryString(ItemVersion version) {
    return getExternalIdSummaryStringPrefixed(version, "");
  }

  @Nullable
  @CanBlock
  public String getExternalIdString(ItemVersion version) {
    return getItemId(version);
  }

  public boolean isOffline() {
    return false;
  }

  @NotNull
  @Override
  public BasicScalarModel<AutoSyncMode> getAutoSyncMode() {
    if(!myAutoSyncMode.isContentKnown()) {
      myAutoSyncMode.setValue(readAutoSyncMode());
      myAutoSyncMode.getEventSource().addStraightListener(new ScalarModel.Adapter<AutoSyncMode>() {
        @Override
        public void onScalarChanged(ScalarModelEvent<AutoSyncMode> e) {
          writeAutoSyncMode(e.getNewValue());
        }
      });
    }
    return myAutoSyncMode;
  }

  @NotNull
  private AutoSyncMode readAutoSyncMode() {
    final Configuration config = getConnectionConfig(AUTO_SYNC_SUBSET);
    final String id = config.getSetting(AUTO_SYNC_MODE_SETTING, AutoSyncMode.AUTOMATIC.getId());
    return AutoSyncMode.forId(id);
  }


  private void writeAutoSyncMode(@NotNull AutoSyncMode mode) {
    final Configuration config = getConnectionConfig(AUTO_SYNC_SUBSET);
    if(mode == AutoSyncMode.AUTOMATIC) {
      config.removeSettings(AUTO_SYNC_MODE_SETTING);
    } else {
      config.setSetting(AUTO_SYNC_MODE_SETTING, mode.getId());
    }
  }
}
