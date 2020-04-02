package com.almworks.explorer.loader;

import com.almworks.api.application.*;
import com.almworks.api.container.*;
import com.almworks.api.engine.*;
import com.almworks.container.TestContainer;
import com.almworks.engine.TestProvider;
import com.almworks.items.api.*;
import com.almworks.items.sync.EditCommit;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.edit.SyncFixture;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.TODO;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.model.BasicScalarModel;
import com.almworks.util.model.SetHolderModel;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.tests.MockProxy;
import org.almworks.util.ExceptionUtil;
import org.almworks.util.RuntimeInterruptedException;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;
import util.concurrent.Synchronized;
import util.concurrent.SynchronizedBoolean;

import static org.almworks.util.Collections15.arrayList;

/**
 * Provides setup for tests that use many Application and Engine objects. <br>
 * !NB! Uses {@link MockProxy} extensively, so <b>inheritors' names must end with RTests</b>. This is done so that these tests are run <b>before</b> obfuscation, otherwise mock proxies will not work.
 */
public abstract class ApplicationFixture extends SyncFixture {
  public static final DBAttribute<String> TEXT = DBAttribute.String("text", "text");
  public static final DBAttribute<Integer> NUM = DBAttribute.Int("num", "num");
  public static final String INIT_TEXT = "~";
  public static final Integer INIT_NUM = 239;
  public static DBItemType ITEM_TYPE = new DBItemType("itemType");
  protected long myItem;
  protected long myConnectionItem;
  protected ItemModelRegistryImpl myRegistry;
  protected MockProxy<Engine> myEngine;
  protected ComponentContainer myContainer;
  protected final SetHolderModel<Object> myProblems = new SetHolderModel<Object>();
  protected final ItemProvider myProvider = new TestProvider(db);

  static {
    ITEM_TYPE.initialize(SyncAttributes.IS_PRIMARY_TYPE, Boolean.TRUE);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setWriteToStdout(true);
    try {
      // order is significant
      // 1. connection item, item in that connection are created
      initPrimaryAndConnectionItems();
      // 2. Engine proxy is created
      myEngine = createEngine();
      // 3. Connection is created for the connection item created in 1), registered in Engine created in 2)
      registerConnection(myEngine, myConnectionItem);
      // 4. Engine object is provided in the container
      myContainer = createContainer(myEngine);
      // 5. Uses Engine
      myRegistry = createAndStartModelRegistry(myEngine.getObject(), myContainer);
    } catch (Exception e) {
      super.tearDown();
      ExceptionUtil.rethrowNullable(e);
    }
  }

  private void initPrimaryAndConnectionItems() {
    db.writeForeground(new WriteTransaction<Object>() {
      @Override
      public Object transaction(DBWriter writer) throws DBOperationCancelledException {
        myConnectionItem = writer.nextItem();
        myItem = writer.nextItem();
        writer.setValue(myItem, SyncAttributes.EXISTING, true);
        writer.setValue(myItem, DBAttribute.TYPE, writer.materialize(ITEM_TYPE));
        writer.setValue(myItem, TEXT, INIT_TEXT);
        writer.setValue(myItem, NUM, INIT_NUM);
        writer.setValue(myItem, SyncAttributes.CONNECTION, myConnectionItem);
        return null;
      }
    }).waitForCompletion();
  }

  private MockProxy<Engine> createEngine() {
    MockProxy<Engine> engine = MockProxy.create(Engine.class, "engine");
    engine.putProxyValue("getSynchronizer", Synchronizer.class).handleObjectMethods().method("getProblems").returning(myProblems).ignore();
    return engine;
  }

  private void registerConnection(MockProxy<Engine> engine, long connectionItem) {
    MockProxy<Connection> connection = MockProxy.create(Connection.class, "connection").handleObjectMethods();
    connection.putValue("getState", BasicScalarModel.createConstant(ConnectionState.READY));
    connection.method("getItemUrl").returning("http://mock.url").ignore();
    connection.method("getConnectionItem").returning(connectionItem).ignore();
    connection.method("getProvider").returning(myProvider).ignore();
    engine.putProxyValue("getConnectionManager", ConnectionManager.class).putValue("findByItem", connectionItem, connection.getObject());
  }

  private ComponentContainer createContainer(MockProxy<Engine> engine) {
    MutableComponentContainer container = new TestContainer();
    container.registerActor(Engine.ROLE, engine.getObject());
    container.registerActor(Database.ROLE, db);
    return container;
  }

  private static MockProxy<EventRouter> createEventRouter() {
    MockProxy<EventRouter> eventRouter = new MockProxy<EventRouter>(EventRouter.class, "eventRouter").handleObjectMethods();
    eventRouter.method("addListener").paramsCount(3).ignore();
    return eventRouter;
  }

  private ItemModelRegistryImpl createAndStartModelRegistry(Engine engine, ComponentContainer container) {
    ItemModelRegistryImpl registry = new ItemModelRegistryImpl(engine, db, container, new AutoAddedModelKey[] {}, null);
    registry.start();
    return registry;

  }

  protected static EditCommit notifyOnTransactionEnd(EditCommit commit, @Nullable final Boolean isSuccess, final SynchronizedBoolean expectedCommitResult) {
    return AggregatingEditCommit.toAggregating(commit).addProcedure(null, new EditCommit.Adapter() {
      @Override
      public void onCommitFinished(boolean success) {
        if (isSuccess == null || isSuccess.equals(success)) {
          expectedCommitResult.set(true);
        }
      }
    });
  }

  static void registerMetaInfo(ModelKey<?>... keys) {
    assertNotNull(keys);
    assertNotEqual(0, keys.length);
    MockProxy<MetaInfo> metaInfo = MockProxy.create(MetaInfo.class, "metaInfo");
    metaInfo.putValue("getKeys", arrayList(keys));
    MetaInfo.REGISTRY.registerMetaInfo(ITEM_TYPE, metaInfo.getObject());
  }

  protected static class MockModelKey<T> extends AbstractModelKey<T> {
    private final SynchronizedBoolean myAddChangesLock = new SynchronizedBoolean(false);
    private final Synchronized<String> myProblem = new Synchronized<String>(null);
    private final DBAttribute<T> myAttr;
    private DataPromotionPolicy myDataPromotionPolicy = null;

    public MockModelKey(DBAttribute<T> attr) {
      super("mockey");
      myAttr = attr;
    }

    public void setModelValue(ItemUiModelImpl model, T newValue) {
      model.getModelMap().put(getModelKey(), newValue);
    }

    public <SM>SM getModel(Lifespan lifespan, ModelMap model, Class<SM> aClass) {
      return null;
    }

    public void addChanges(UserChanges changes) {
      try {
        myAddChangesLock.waitForValue(true);
      } catch (InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
      String problem = myProblem.get();
      if (problem != null)
        changes.addProblem(problem);
      changes.getCreator().setValue(myAttr, changes.getNewValue(getModelKey()));
    }

    public void passAddChanges() {
      passAddChanges(null);
    }

    public void passAddChanges(String problem) {
      myProblem.set(problem);
      myAddChangesLock.set(true);
    }

    @Override
    public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
      setValue(values, itemVersion.getValue(myAttr));
    }

    public boolean isSystem() {
      return false;
    }

    @NotNull
    public CanvasRenderer<PropertyMap> getRenderer() {
      throw TODO.shouldNotHappen("Tests only");
    }

    public ModelMergePolicy getMergePolicy() {
      return null;
    }

    @Override
    public DataPromotionPolicy getDataPromotionPolicy() {
      return myDataPromotionPolicy != null ? myDataPromotionPolicy : super.getDataPromotionPolicy();
    }

    public void setDataPromotionPolicy(DataPromotionPolicy dataPromotionPolicy) {
      myDataPromotionPolicy = dataPromotionPolicy;
    }
  }
}
