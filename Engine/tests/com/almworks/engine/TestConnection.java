package com.almworks.engine;

import com.almworks.api.application.*;
import com.almworks.api.application.order.Order;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.constraint.Constraint;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.*;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.container.TestContainer;
import com.almworks.engine.items.ItemStorageAdaptor;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.util.SyncAttributes;
import com.almworks.spi.provider.ConnectionContext;
import com.almworks.util.*;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.commons.Procedure;
import com.almworks.util.config.*;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.*;
import com.almworks.util.models.TableColumnAccessor;
import com.almworks.util.progress.Progress;
import com.almworks.util.progress.ProgressSource;
import com.almworks.util.threads.*;
import com.almworks.util.ui.UIComponentWrapper;
import com.almworks.util.ui.actions.AnAction;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.Collections15;
import org.almworks.util.Failure;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;
import org.picocontainer.Startable;

import java.io.IOException;
import java.util.*;

public class TestConnection implements Connection, Startable {
  private final BasicScalarModel<ConnectionState> myState = BasicScalarModel.createWithValue(ConnectionState.INITIAL, true);
  private final Configuration myConfiguration;
  private final ComponentContainer mySubcontainer;
  private final TestCommonMeta myMeta;
  private final ItemProvider myProvider;
  private final TestSynchronizer mySynchronizer;
  private final SetHolderModel<SyncTask> mySyncTaskModel = new SetHolderModel<SyncTask>();
  private final String myId;
  private final Database myDatabase;

  private TestPrivateMeta myPrivateMeta;
  private ConnectionViews myViews;

  public TestConnection(Database database, TestCommonMeta meta, ItemProvider provider, ComponentContainer container) {
    this(database, meta, provider, String.valueOf(System.currentTimeMillis()), container);
  }

  public TestConnection(Database database, TestCommonMeta meta, ItemProvider provider, String id, ComponentContainer container) {
    try {
      myId = id;
      myMeta = meta;
      myProvider = provider;
      mySubcontainer = container;
      myConfiguration = ConfigurationUtil.copy(JDOMConfigurator.parse("<config><name>TestConnection</name></config>"));
      mySynchronizer = new TestSynchronizer();
      myDatabase = database;
    } catch (IOException e) {
      throw new Failure(e);
    } catch (BadFormatException e) {
      throw new Failure(e);
    }
  }

  @CanBlock
  public static TestConnection createAndStart(Database db, TestContainer container) {
    TestConnection conn = new TestConnection(db, new TestCommonMeta(), new TestProvider(db), container);
    conn.start();
    return conn;
  }

  @Nullable
  @CanBlock
  public String getExternalIdSummaryString(ItemVersion version) {
    throw new UnsupportedOperationException();
  }

  public ReadonlyConfiguration getConfiguration() {
    return myConfiguration;
  }

  public long getConnectionItem() {
    return myPrivateMeta.connectionItem;
  }

  @Override
  public DBIdentifiedObject getConnectionRef() {
    return null;
  }

  public String getConnectionID() {
    return myId;
  }

  @ThreadAWT
  @Nullable
  public AnAction createDropChangeAction(ItemHypercube target, String frameId, boolean move) {
    return null;
  }

  public ConnectionSynchronizer getConnectionSynchronizer() {
    return mySynchronizer;
  }


  @Deprecated
  @CanBlock
  @Nullable
  public String getItemId(ItemVersion version) {
    return null;
  }

  @Deprecated
  @CanBlock
  @Nullable
  public String getItemSummary(ItemVersion version) {
    return null;
  }

  public boolean hasCapability(Capability capability) {
    throw TODO.notImplementedYet();
  }

  public long getPrototypeItem(DBItemType itemType) {
    if (itemType == TestCommonMeta.NOTE)
      return myPrivateMeta.prototypeItem;
    throw new IllegalArgumentException("no prototype for type " + itemType);
  }

  public ItemProvider getProvider() {
    return myProvider;
  }

  @Nullable
  @CanBlock
  public String getExternalIdString(ItemVersion version) {
    return null;
  }

  public ConnectionContext getContext() {
    return null;
  }

  @Nullable
  @ThreadSafe
  public String getDisplayableItemId(ItemWrapper wrapper) {
    return null;
  }

  public ItemHypercube adjustHypercube(@NotNull ItemHypercube hypercube) {
    return hypercube;
  }

  public Constraint adjustConstraint(@NotNull Constraint constraint) {
    return constraint;
  }

  @Override
  public void adjustView(@NotNull DBFilter view, Lifespan life, Procedure<DBFilter> cont) {
  }

  public boolean isOffline() {
    return false;
  }

  public CollectionModel<RemoteQuery> getRemoteQueries() {
    return null;
  }

  public void removeConnection(Runnable onConnectionItemsRemoved) {
    stopConnection();
    myState.setValue(ConnectionState.REMOVED);
    ThreadGate.AWT_OPTIMAL.execute(onConnectionItemsRemoved);
  }

  public void startConnection() {
    boolean starting = myState.commitValue(ConnectionState.STOPPED, ConnectionState.STARTING) ||
      myState.commitValue(ConnectionState.INITIAL, ConnectionState.STARTING);
    if (starting) {
      start();
      myState.setValue(ConnectionState.READY);
    }
  }

  public void stopConnection() {
    if (myState.commitValue(ConnectionState.READY, ConnectionState.STOPPING))
      myState.setValue(ConnectionState.STOPPED);
  }

  public void update(ReadonlyConfiguration configuration) throws ConfigurationException {
    myConfiguration.clear();
    ConfigurationUtil.copyTo(configuration, myConfiguration);
  }

  public Collection<DBItemType> getPrimaryTypes() {
    return Collections.singleton(TestCommonMeta.NOTE);
  }

  public ConnectionViews getViews() {
    return myViews;
  }

  public ComponentContainer getConnectionContainer() {
    return mySubcontainer;
  }

  public ScalarModel<ConnectionState> getState() {
    return myState;
  }

  public String buildDefaultQueriesXML() {
    return null;
  }

  @Override
  public SyncTask synchronizeItemView(Constraint validRemoteQueryConstraint, DBFilter view,
    LongList localResult, String queryName, Procedure<SyncTask> runFinally) {
    throw TODO.notImplementedYet();
  }

  public void uploadItems(LongList items) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SyncTask downloadItemDetails(LongList items) {
    return null;
  }

  @ThreadAWT
  @NotNull
  public Pair<DBFilter, Constraint> getViewAndConstraintForUrl(String url) throws CantPerformExceptionExplained {
    throw TODO.notImplementedYet();
  }

  @Override
  public ItemSource getItemSourceForUrls(Iterable<String> urls) {
    return null;
  }

  public boolean isItemUrl(String itemUrl) {
    return false;
  }

  @Override
  public String getItemIdForUrl(String itemUrl) {
    return null;
  }

  @CanBlock
  @Nullable
  public Date getItemTimestamp(ItemVersion version) {
    throw new UnsupportedOperationException();
  }

  @CanBlock
  @Nullable
  public String getItemLongDescription(ItemVersion version) {
    throw new UnsupportedOperationException();
  }

  @CanBlock
  @Nullable
  public String getItemShortDescription(ItemVersion version) {
    throw new UnsupportedOperationException();
  }

  public ScalarModel<InitializationState> getInitializationState() {
    throw new UnsupportedOperationException();
  }

  public void requestReinitialization() {
    throw new UnsupportedOperationException();
  }

  public UIComponentWrapper getConnectionStateComponent() {
    throw TODO.notImplementedYet();
  }

  public SetHolder<SyncTask> getSyncTasks() {
    return mySyncTaskModel;
  }

  public QueryUrlInfo getQueryURL(Constraint constraint, DBReader reader) {
    throw new UnsupportedOperationException();
  }

  public AListModel<? extends TableColumnAccessor<LoadedItem, ?>> getMainColumns() {
    throw TODO.notImplementedYet();
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

  @NotNull
  public AListModel<? extends ConstraintDescriptor> getDescriptors() {
    return AListModel.EMPTY;
  }

  @ThreadAWT
  public AListModel<? extends Order> getOrdersModel() {
    return AListModel.EMPTY;
  }

  @Nullable
  @ThreadSafe
  public ConstraintDescriptor getDescriptorByIdSafe(String id) {
    return null;
  }

  public void start() {
    Threads.assertLongOperationsAllowed();
    DBResult result = myDatabase.writeForeground(new WriteTransaction() {
      public TestPrivateMeta transaction(DBWriter writer) {
        myMeta.materialize(writer);
        myPrivateMeta = new TestPrivateMeta(writer);
        myViews = new TestConnectionViews(myDatabase, myPrivateMeta);
        return null;
      }
    });
    result.waitForCompletion();
    assert result.isSuccessful();
  }

  public void stop() {
  }

  @Override
  public String getItemUrl(ItemVersion localVersion) {
    return null;
  }


  @Nullable
  public ScalarModel<Collection<RemoteQuery2>> getRemoteQueries2() {
    return null;
  }

  @CanBlock
  @NotNull
  public Collection<? extends Attachment> getItemAttachments(ItemVersion primaryItem) {
    return Collections15.emptyCollection();
  }

  public boolean matchAllWords(long item, char[][] charWords, String[] stringWords, DBReader reader) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public BasicScalarModel<AutoSyncMode> getAutoSyncMode() {
    return BasicScalarModel.createConstant(AutoSyncMode.AUTOMATIC);
  }

  public boolean isAllowedByLicense() {
    return true;
  }

  public class TestSynchronizer extends SimpleModifiable implements ConnectionSynchronizer {
    private final BasicScalarModel<State> myState = BasicScalarModel.createWithValue(State.NEVER_HAPPENED, true);
    private final SetHolderModel<SyncProblem> myProblems = new SetHolderModel<SyncProblem>();

    public void synchronize(SyncParameters parameters) {
      // todo
      setState(State.WORKING);
      setState(State.DONE);
    }

    public void cancel() {
    }

    public boolean isCancellableState() {
      return false;
    }

    public Connection getConnection() {
      return TestConnection.this;
    }

    public SetHolder<SyncProblem> getProblems() {
      return myProblems;
    }

    public boolean removeProblem(SyncProblem problem) {
      return false;
    }

    public ProgressComponentWrapper getProgressComponentWrapper() {
      return null;
    }

    public ScalarModel<State> getState() {
      return myState;
    }

    public long getLastCommittedCN() {
      throw TODO.notImplementedYet();
    }

    public String getTaskName() {
      return "TestConnection";
    }

    @Override
    public SpecificItemActivity getSpecificActivityForItem(long itemId, @Nullable Integer serverId) {
      return SpecificItemActivity.OTHER;
    }

    private void setState(State state) {
      myState.setValue(state);
      fireChanged();
    }

    public ProgressSource getProgressSource() {
      return Progress.STUB;
    }
  }

  private class TestConnectionViews implements ConnectionViews {
    private final DBFilter myConnectionItems;
    private final DBFilter myOutbox;

    public TestConnectionViews(Database db, TestPrivateMeta meta) {
      myConnectionItems = new DBFilter(db, DPEquals.create(SyncAttributes.CONNECTION, meta.connectionItem));
      myOutbox = new DBFilter(db, ItemStorageAdaptor.modified());
    }

    @Override
    public DBFilter getConnectionItems() {
      return myConnectionItems;
    }

    @NotNull
    @Override
    public Modifiable connectionItemsChange() {
      return Modifiable.NEVER;
    }

    @Override
    public DBFilter getOutbox() {
      return myOutbox;
    }
  }
}
