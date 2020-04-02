package com.almworks.bugzilla.provider;

import com.almworks.api.application.*;
import com.almworks.api.application.order.Order;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.constraint.*;
import com.almworks.api.container.MutableComponentContainer;
import com.almworks.api.engine.*;
import com.almworks.api.engine.util.MatchAllHelper;
import com.almworks.api.explorer.gui.SimpleModelKey;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.api.syncreg.ItemHypercubeUtils;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.integration.data.BooleanChart;
import com.almworks.bugzilla.provider.attachments.AttachmentInfo;
import com.almworks.bugzilla.provider.attachments.AttachmentsModelKey;
import com.almworks.bugzilla.provider.custom.BugzillaCustomField;
import com.almworks.bugzilla.provider.datalink.flags2.HasFlags;
import com.almworks.bugzilla.provider.datalink.flags2.UIFlagData;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.bugzilla.provider.datalink.schema.attachments.AttachmentsLink;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.integers.LongIterator;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.SyncAttributes;
import com.almworks.spi.provider.AbstractConnection;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import com.almworks.util.*;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.SegmentedListModel;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.commons.*;
import com.almworks.util.config.ConfigurationException;
import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.i18n.Local;
import com.almworks.util.model.CollectionModel;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.models.TableColumnAccessor;
import com.almworks.util.properties.Role;
import com.almworks.util.threads.*;
import com.almworks.util.ui.UIComponentWrapper;
import com.almworks.util.ui.actions.*;
import org.almworks.util.*;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.util.*;

import static org.almworks.util.Collections15.hashSet;

public class BugzillaConnection extends AbstractConnection<BugzillaProvider, BugzillaContextImpl> {
  public static final Role<BugzillaConnection> ROLE = Role.role(BugzillaConnection.class);
  private final ProductDependenciesTracker myDependenciesTracker;

  private final Lazy<UIComponentWrapper> myInformationPanel = new Lazy<UIComponentWrapper>() {
    @NotNull
    public UIComponentWrapper instantiate() {
      return BugzillaConnectionPanel.getLazyWrapper(myContainer);
    }
  };

  private final HasFlags myHasFlags;
  private AListModel<? extends TableColumnAccessor<LoadedItem, ?>> myColumns;
  private AListModel<? extends TableColumnAccessor<LoadedItem, ?>> myFlagColumns;

  public BugzillaConnection(ItemProvider provider, String connectionID, ReadonlyConfiguration configuration,
    MutableComponentContainer container) throws ConfigurationException
  {
    super((BugzillaProvider) provider, container, connectionID);

    myContainer.registerActor(ROLE, this);

    PrivateMetadata privateMetadata = new PrivateMetadata(this, ((BugzillaProvider) provider).getCommonMetadata());
    myContainer.registerActor(PrivateMetadata.ROLE, privateMetadata);
    myDependenciesTracker = new ProductDependenciesTracker(myContainer, privateMetadata);
    myContainer.registerActor(ProductDependenciesTracker.ROLE, myDependenciesTracker);

    myContainer.registerActorClass(BugzillaContext.ROLE, BugzillaContextImpl.class);
    BugzillaContextImpl context = (BugzillaContextImpl) myContainer.getActor(BugzillaContext.ROLE);
    assert context != null;
    context.configure(configuration);
    initContext(context);

    BugzillaSynchronizer synchronizer = new BugzillaSynchronizer(getContext(), myContainer);
    myContainer.registerActor(BugzillaSynchronizer.ROLE, synchronizer);
    myContainer.registerActor(BugUploadQueue.ROLE, new BugUploadQueue(getContext(), myContainer));
    myContainer.registerActorClass(BugzillaConnectionInitializer.ROLE, BugzillaConnectionInitializer.class);
    myHasFlags = new HasFlags(this);
  }

  public synchronized CollectionModel<RemoteQuery> getRemoteQueries() {
    return getContext().getRemoteQueries();
  }

  public AListModel<? extends Order> getOrdersModel() {
    return getContext().getCustomFields().getOrders();
  }

  public ReadonlyConfiguration getConfiguration() {
    return getContext().getConfiguration().getValue().getConfiguration();
  }

  public synchronized void update(ReadonlyConfiguration configuration) throws ConfigurationException {
    getContext().configure(configuration);
  }

  public Collection<DBItemType> getPrimaryTypes() {
    return Collections.singleton(Bug.typeBug);
  }

  public ConnectionViews getViews() {
    return getContext().getViews();
  }

  public ScalarModel<ConnectionState> getState() {
    return getContext().getState();
  }

  public String buildDefaultQueriesXML() {
    ConnectionState state = getContext().getState().getValue();
    if (state != ConnectionState.READY)
      return null;
    InitializationState initializationState = getContext().getInitializationState().getValue();
    if (initializationState == null || !initializationState.isInitialized())
      return null;
    BugzillaDefaultQueriesBuilder queriesBuilder = myContainer.instantiate(BugzillaDefaultQueriesBuilder.class);
    return queriesBuilder.buildXML();
  }

  private void checkReady() {
    assert getContext().getState().getValue() == ConnectionState.READY : this + " is not ready";
  }

  public long getConnectionItem() {
    if (!isMaterialized())
      throw new IllegalStateException("not initialized");
    return getContext().getPrivateMetadata().thisConnectionItem();
  }

  @Override
  public DBIdentifiedObject getConnectionRef() {
    return getContext().getPrivateMetadata().thisConnection;
  }

  protected void doMaterialize(DBWriter writer) {
    getContext().getPrivateMetadata().materialize(writer);
  }

  public void startConnection() {
    super.startConnection();
    myHasFlags.start();
  }

  @Override
  public long getPrototypeItem(DBItemType itemType) {
    if (Bug.typeBug.equals(itemType))
      return getContext().getPrivateMetadata().bugPrototypeItem();
    throw new IllegalArgumentException("no prototype for type " + itemType);
  }

  @Override
  public SyncTask synchronizeItemView(Constraint constraint, DBFilter view,
    @Nullable LongList localResult, String queryName, Procedure<SyncTask> runFinally)
  {
    ItemViewSynchronizer synchronizer = new ItemViewSynchronizer(
      myProvider.getViewSynchronizerRunner(), constraint, view, queryName, getContext(), myContainer, runFinally);
    synchronizer.startTask();
    return synchronizer;
  }

  @Override
  public void uploadItems(LongList items) {
    DECL.assumeThreadMayBeAWT();
    // todo check if started (2010: seems like it is not necessary now)
    if (!isMaterialized())
      throw new Failure("connection not initialized");
    BugUploadQueue uploader = myContainer.getActor(BugUploadQueue.ROLE);
    assert uploader != null;
    for (LongIterator it = items.iterator(); it.hasNext();) {
      uploader.enqueue(it.next());
    }
  }

  @Override
  @CanBlock
  public String getItemUrl(ItemVersion version) {
    Integer bugId = version.getValue(Bug.attrBugID);
    if (bugId != null) {
      String url = getBugUrlById(bugId);
      if (url != null) return url;
    }
    return super.getItemUrl(version);
  }

  @Nullable
  public String getBugUrlById(Integer bugId) {
    BugzillaContextImpl context = getContext();
    try {
      return context.getIntegration(BugzillaAccessPurpose.IMMEDIATE_DOWNLOAD).getArtifactURL(bugId);
    } catch (ConnectionNotConfiguredException e) {
      return null;
    }
  }

  @ThreadAWT
  @NotNull
  public Pair<DBFilter, Constraint> getViewAndConstraintForUrl(String url) throws CantPerformExceptionExplained {
    Integer id = getBugIdFromUrl(url);
    if (id == null)
      throw new CantPerformExceptionExplained("Cannot understand URL " + url);
    DBFilter view = getViews().getConnectionItems();
    DBAttribute<Integer> idAttr = Bug.attrBugID;
    view = view.filter(DPEquals.create(idAttr, id));
    Constraint constraint = FieldIntConstraint.Simple.equals(idAttr, BigDecimal.valueOf(id));
    return Pair.create(view, constraint);
  }

  @Override
  public ItemSource getItemSourceForUrls(Iterable<String> urls) {
    BugzillaContextImpl context = getContext();
    try {
      // todo misguiding Bugzilla access purpose
      final BugzillaIntegration integration = context.getIntegration(BugzillaAccessPurpose.IMMEDIATE_DOWNLOAD);
      Set<Integer> ids = hashSet();
      for (String url : urls) {
        Integer id = integration.getBugIdFromURL(url);
        if (id == null) return null;
        ids.add(id);
      }
      return new UrlBugSource(ids, context, myContainer, myProvider.getViewSynchronizerRunner());
    } catch (ConnectionNotConfiguredException e) {
      Log.warn("Connection is not ready for loading urls", new Throwable());
      return null;
    }
  }

  public UIComponentWrapper getConnectionStateComponent() {
    return myInformationPanel.get();
  }

  public ProductDependenciesTracker getDependenciesTracker() {
    return myDependenciesTracker;
  }

  @Nullable
  private BooleanChart buildBooleanChart(@NotNull Constraint constraint, DBReader reader) {
    BugzillaContextImpl context = getContext();
    ConnectionState state = context.getState().getValue();
    if (state == null || !state.isReady())
      return null;
    return new BooleanChartMaker(this).createBooleanChart(constraint, reader);
  }

  public BugzillaContextImpl getContext() {
    return super.getContext();
  }

  @Nullable
  @ThreadSafe
  public String getDisplayableItemId(ItemWrapper wrapper) {
    Integer id = wrapper.getModelKeyValue(BugzillaKeys.id);
    return id == null ? null : "#" + id;
  }

  public ItemHypercube adjustHypercube(@NotNull ItemHypercube hypercube) {
    BugzillaContextImpl context = getContext();
    DBAttribute<Long> productAttr = Bug.attrProduct;
    Set<Long> limitingProducts = context.getLimitingProductsArtifacts(hypercube);
    return !limitingProducts.isEmpty() ? ItemHypercubeUtils.ensureValuesIncludedForAxis(hypercube, productAttr, limitingProducts) : hypercube;
  }

  public Constraint adjustConstraint(@NotNull Constraint constraint) {
    BugzillaContextImpl context = getContext();
    DBAttribute<Long> productAttr = Bug.attrProduct;
    Set<Long> limitingProducts = context.getLimitingProductsArtifacts(null);
    return !limitingProducts.isEmpty() ? CompositeConstraint.Simple.and(constraint, FieldSubsetConstraint.Simple.intersection(productAttr, limitingProducts)) : constraint;
  }

  @Override
  public void adjustView(@NotNull final DBFilter view, Lifespan life, @NotNull final Procedure<DBFilter> cont) {
    if(life.isEnded()) {
      return;
    }
    final BugzillaContextImpl context = getContext();
    final DBAttribute<Long> productAttr = Bug.attrProduct;
    context.getConfiguredProductsItems(life, new Procedure<Set<Long>>() {
      @Override
      public void invoke(Set<Long> products ) {
        cont.invoke(products.isEmpty() ? view : view.filter(DPEquals.equalOneOf(productAttr, products)));
      }
    });
  }

  @Nullable
  @CanBlock
  public QueryUrlInfo getQueryURL(@NotNull Constraint constraint, DBReader reader) {
    try {
      QueryUrlInfo result = new QueryUrlInfo();
      result.setUrl(BugzillaUtil.buildRemoteQueryUrl(buildBooleanChart(constraint, reader), getContext(), true));
      return result;
    } catch (ConnectionNotConfiguredException e) {
      Log.debug("connection is not configured");
      return null;
    }
  }

  public boolean isItemUrl(String itemUrl) {
    return getItemIdForUrl(itemUrl) != null;
  }

  @Nullable
  public Integer getBugIdFromUrl(String itemUrl) {
    BugzillaContextImpl context = getContext();
    try {
      BugzillaIntegration integration = context.getIntegration(BugzillaAccessPurpose.IMMEDIATE_DOWNLOAD);
      return integration.getBugIdFromURL(itemUrl);
    } catch (ConnectionNotConfiguredException e) {
      return null;
    }
  }

  @Override
  public String getItemIdForUrl(String itemUrl) {
    Integer id = getBugIdFromUrl(itemUrl);
    return id == null ? null : ("Bug " + id);
  }

  @Override
  public Date getItemTimestamp(ItemVersion version) {
    return version.getValue(Bug.attrModificationTime);
  }

  @Override
  @CanBlock
  @Nullable
  public String getItemShortDescription(ItemVersion version) {
    Integer id = version.getValue(Bug.attrBugID);
    String shortDescription = version.getValue(Bug.attrSummary);
    if (id == null)
      return "Unknown Artifact";
    String idString = "<b>" + "#" + id + "</b> ";
    if (shortDescription == null)
      return idString + Local.parse("($(" + Terms.key_artifact + ") is not loaded)");
    else
      return idString + shortDescription;
  }

  @Override
  @CanBlock
  @Nullable
  public synchronized String getItemLongDescription(ItemVersion version) {
    return BugExternalPresentation.getExternalPresentation(version, getItemUrl(version));
  }

  @Override
  public String getItemId(ItemVersion version) {
    return String.valueOf(version.getValue(Bug.attrBugID));
  }

  @Override
  public String getItemSummary(ItemVersion version) {
    return version.getValue(Bug.attrSummary);
  }

  public boolean hasCapability(Capability capability) {
    if (capability == Capability.CREATE_ITEM || capability == Capability.EDIT_ITEM) {
      BugzillaContext context = getContext();
      OurConfiguration configuration = context.getConfiguration().getValue();
      if (configuration != null) {
        return !configuration.isAnonymousAccess();
      }
    }

    return false;
  }

  @ThreadAWT
  public AListModel<? extends TableColumnAccessor<LoadedItem, ?>> getMainColumns() {
    Threads.assertAWTThread();
    if (myColumns == null) {
      AListModel<TableColumnAccessor<LoadedItem, ?>> c1 = myProvider.getCommonMetadata().getFieldColumns();
      AListModel<TableColumnAccessor<LoadedItem, ?>> c2 = getContext().getCustomFields().getColumns();
      myColumns = SegmentedListModel.create(c1, c2);
    }
    return myColumns;
  }

  @ThreadAWT
  @Override
  public AListModel<? extends TableColumnAccessor<LoadedItem, ?>> getAuxiliaryColumns() {
    Threads.assertAWTThread();
    if (myFlagColumns == null) {
      myFlagColumns = getCommonMD().myFlags.getColumnsForConnection(getContext().getConnectionLife(), getConnectionItem());
    }
    return myFlagColumns;
  }

  public CommonMetadata getCommonMD() {
    return getContext().getMetadata();
  }

  @Override
  public Comparator<? super TableColumnAccessor<LoadedItem, ?>> getAuxColumnsOrder() {
    return UIFlagData.getColumnsComparator();
  }

  @NotNull
  @Override
  protected AListModel<? extends ConstraintDescriptor> createDescriptorsModel() {
    return SegmentedListModel.create(
        CommonMetadata.getDescriptorsModel(),
        getContext().getCustomFields().getDescriptors(),
        myHasFlags.getDescriptorsModel());
  }

  @ThreadAWT
  @Nullable
  @Override
  public SyncTask downloadItemDetails(LongList items) {
    final BugDetailDownloader downloader = new BugDetailDownloader(getContext(), myContainer, items);
    subscribeToTaskUntilFinalState(downloader);
    downloader.startTask();
    return downloader;
  }

  @Override
  public boolean matchAllWords(long item, char[][] charWords, String[] stringWords, DBReader reader) {
    BugzillaContextImpl context = getContext();
    MatchAllHelper hepler = new MatchAllHelper(charWords);
    if (matchAllWordsText(item, BugzillaKeys.allTextKeys, hepler, reader))
      return true;
//    if (matchAllWordsSingleArtifact(revision, stringWords, keys.allSingleItemKeys))
//      return true;

    Set<BugzillaCustomField> fields = context.getCustomFields().copyExisting();
    if (fields != null) {
      for (BugzillaCustomField field : fields) {
        if (field.isTextSearchEnabled()) {
          if (hepler.matchAttr(item, field.getAttribute(), reader)) {
            return true;
          }
        }
      }
    }

    String[] texts = BugzillaKeys.comments.getCommentsText(SyncUtils.readTrunk(reader, item));
    for (String text : texts) {
      if (hepler.matchString(text))
        return true;
    }
    return false;
  }

  @Override
  @Nullable
  @CanBlock
  public String getExternalIdSummaryString(ItemVersion version) {
    return getExternalIdSummaryStringPrefixed(version, "#");
  }

  @Override
  public String getExternalIdString(ItemVersion version) {
    String id = getItemId(version);
    if (id == null)
      return null;
    else
      return "#" + id;
  }

  @ThreadAWT
  @Nullable
  public AnAction createDropChangeAction(ItemHypercube target, String frameId, boolean move) {
    return new BugzillaDropChangeAction(this, frameId, target, move);
  }

  @Nullable
  public static BugzillaContext getContext(ItemWrapper a) {
    Connection connection = a.getConnection();
    if (!(connection instanceof BugzillaConnection)) return null;
    return ((BugzillaConnection) connection).getContext();
  }

  @Nullable
  public static BugzillaConnection getInstance(ModelMap model) {
    return getInstance(LoadedItemServices.VALUE_KEY.getValue(model));
  }

  public static BugzillaConnection getInstance(LoadedItemServices wrapper) {
    if (wrapper == null) return null;
    Connection connection = wrapper.getConnection();
    return Util.castNullable(BugzillaConnection.class, connection);
  }

  @Nullable
  public static BugzillaConnection getInstance(ItemWrapper item) {
    if (item == null) return null;
    return getInstance(item.services());
  }

  private boolean matchAllWordsText(long item, List<SimpleModelKey<?, String>> keys,
    MatchAllHelper hepler, DBReader reader)
  {
    for (SimpleModelKey<?, String> key : keys) {
      if (hepler.matchAttr(item, key.getAttribute(), reader))
        return true;
    }
    return false;
  }

  @NotNull
  public Collection<? extends Attachment> getItemAttachments(final ItemVersion primaryItem) {
    DBAttribute<Long> attachmentSlaveAttribute = AttachmentsLink.attrMaster;
    final ItemKeyCache resolver = getContext().getActor(NameResolver.ROLE).getCache();
    BoolExpr<DP> attachmentExpr = DPEquals.create(attachmentSlaveAttribute, primaryItem.getItem());
    return primaryItem.getReader().query(attachmentExpr).fold(Collections15.<AttachmentInfo>arrayList(), new LongObjFunction2<List<AttachmentInfo>>() {
      @Override
      public List<AttachmentInfo> invoke(long a, List<AttachmentInfo> b) {
        b.add(AttachmentsModelKey.createAttachmentInfo(getContext(), resolver, a, primaryItem));        
        return b;
      }
    });
  }

  public static BugzillaConnection getInstance(ActionContext context) throws CantPerformException {
    ItemWrapper wrapper = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    return CantPerformException.ensureNotNull(getInstance(wrapper));
  }

  @Nullable
  public static BugzillaConnection getInstance(Engine engine, ItemVersion item) {
    Long conn = item.getValue(SyncAttributes.CONNECTION);
    if (conn == null || conn < 0) return null;
    Connection connection = engine.getConnectionManager().findByItem(conn);
    return Util.castNullable(BugzillaConnection.class, connection);
  }

  public boolean hasFlags() {
    return myHasFlags.mayHasFlags();
  }

  public void updateHasFlags(boolean hasBugFlags) {
    myHasFlags.update(hasBugFlags);
  }
}