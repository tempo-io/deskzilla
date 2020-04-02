package com.almworks.bugzilla.provider;

import com.almworks.api.application.NameResolver;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.download.DownloadOwner;
import com.almworks.api.engine.*;
import com.almworks.application.NameResolverImpl;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.provider.datalink.BugzillaAttributeLink;
import com.almworks.bugzilla.provider.datalink.ReferenceLink;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.container.TestContainer;
import com.almworks.engine.EngineImpl;
import com.almworks.engine.TestConnection;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.sync.*;
import com.almworks.items.sync.edit.SyncFixture;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.config.Configuration;
import com.almworks.util.config.ConfigurationException;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.InstanceProvider;
import com.almworks.util.model.ArrayListCollectionModel;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.properties.Role;
import org.almworks.util.ExceptionUtil;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.util.Map;

public abstract class BugzillaConnectionFixture extends SyncFixture {
  protected ConnectorException myExceptionReported;
  protected TestContext myContext;
  protected TestConnection myConnection;
  private TestContainer myContainer;
  private static CommonMetadata myMetadata;
  // guarded by myLock
  private PrivateMetadata myPrivateMetadata;

  private final Object myLock = new Object();

  protected void setUp() throws Exception {
    super.setUp();
    try {
      setWriteToStdout(true);
      clean();
      init();
    } catch (Throwable t) {
      super.tearDown();
      ExceptionUtil.rethrowNullable(t);
    }
  }

  protected void init() throws ConfigurationException {
    myContainer = new TestContainer();
    myContainer.registerActor(Database.ROLE, db);
    NameResolverImpl nameResolver = new NameResolverImpl(new EngineImpl(new ItemProvider[0], myContainer, null, db));
    myContainer.registerActor(NameResolver.ROLE, nameResolver);
    Context.add(InstanceProvider.instance(nameResolver, NameResolver.ROLE), "NameResolver");
    Context.globalize();
    myContext = new TestContext();
    myConnection = TestConnection.createAndStart(db, myContainer);
  }

  protected void tearDown() throws Exception {
    clean();
    super.tearDown();
  }

  protected void clean() {
    myConnection = null;
    myExceptionReported = null;
//    myMetadata = null;
//    myPrivateMetadata = null;
    myContext = null;
  }

  protected CommonMetadata metadata() {
    synchronized (BugzillaConnectionFixture.class) {
      if (myMetadata == null) {
        myMetadata = new CommonMetadata(myContainer, Configuration.EMPTY_CONFIGURATION);
      }
      return myMetadata;
    }
  }

  protected PrivateMetadata privateMetadata() {
    synchronized (myLock) {
      if (myPrivateMetadata == null) {
        myPrivateMetadata = new PrivateMetadata(myConnection, myMetadata);
      }
      return myPrivateMetadata;
    }
  }

  // do not call inside transaction
  protected long createItem(final BugzillaAttribute attribute, final String keyValue) throws InterruptedException {
    assertFalse(db.isDbThread());
    final long[] created = new long[1];
    commitAndWait(new EditCommit.Adapter() {
      @Override
      public void performCommit(EditDrain drain) throws DBOperationCancelledException {
        created[0] = createItem(attribute, keyValue, drain);
      }
    });
    return created[0];
  }

  protected long createItem(BugzillaAttribute attribute, String keyValue, DBDrain drain) {
    final BugzillaAttributeLink link = CommonMetadata.ATTR_TO_LINK.get(attribute);
    assertTrue(link instanceof ReferenceLink);
    return ((ReferenceLink) link).getOrCreateReferent(privateMetadata(), keyValue, drain);
  }

  protected long createEmptyBug() throws InterruptedException {
    final long[] item = new long[1];
    commitAndWait(new EditCommit.Adapter() {
      @Override
      public void performCommit(EditDrain drain) throws DBOperationCancelledException {
        item[0] = createEmptyBug(drain).getItem();
      }
    });
    return item[0];
  }

  protected ItemVersionCreator createEmptyBug(DBDrain drain) {
    ItemVersionCreator creator = drain.createItem();
    setEmptyBugValues(creator);
    return creator;
  }

  private void setEmptyBugValues(ItemVersionCreator creator) {
    creator.setValue(DBAttribute.TYPE, Bug.typeBug);
    creator.setValue(SyncAttributes.CONNECTION, privateMetadata().thisConnection);
  }

  protected class TestContext implements BugzillaContext {
    public TestContext() {
    }

    public ComponentDefaultsTracker getComponentDefaultsTracker() {
      assert false;
      return null;
    }

    @Override
    public OptionalFieldsTracker getOptionalFieldsTracker() {
      assert false;
      return null;
    }

    @Override
    public <T> T getActor(Role<T> role) {
      return myContainer.getActor(role);
    }

    @Override
    public void lockIntegration(Object owner) {
      assert false;
    }

    @Override
    public void unlockIntegration(Object owner) {
      assert false;
    }

    public void requestReinitialization() {

    }

    public PermissionTracker getPermissionTracker() {
      assert false;
      return null;
    }

    public WorkflowTracker getWorkflowTracker() {
      assert false;
      return null;
    }

    public BugzillaCustomFields getCustomFields() {
      throw new UnsupportedOperationException();
    }

    public String getLastInitializationError() {
      throw new UnsupportedOperationException();
    }

    public void setInitializationResult(boolean success, String error) {
      throw new UnsupportedOperationException();
    }

    public ScalarModel<InitializationState> getInitializationState() {
      throw new UnsupportedOperationException();
    }

    public void setInitializationInProgress() {
      throw new UnsupportedOperationException();
    }

    public DBFilter getBugsView() {
      return db.filter(DPEqualsIdentified.create(DBAttribute.TYPE, Bug.typeBug));
    }

    @NotNull
    public ScalarModel<OurConfiguration> getConfiguration() {
      throw new UnsupportedOperationException();
    }

    public BugzillaIntegration getIntegration(BugzillaAccessPurpose purpose) {
      return null;
    }

    public ComponentContainer getContainer() {
      throw new UnsupportedOperationException();
    }

    public BugzillaIntegration getIntegration() {
      throw new UnsupportedOperationException();
    }

    public synchronized CommonMetadata getMetadata() {
      return metadata();
    }

    public PrivateMetadata getPrivateMetadata() {
      return privateMetadata();
    }

    public ArrayListCollectionModel<RemoteQuery> getRemoteQueries() {
      throw new UnsupportedOperationException();
    }

    public Connection getConnection() {
      return myConnection;
    }

    public ScalarModel<ConnectionState> getState() {
      throw new UnsupportedOperationException();
    }

    public DownloadOwner getDownloadOwner() {
      return null;
    }

    public void start() {
    }

    public void stop() {
    }

    public <K, V> Map<K, V> getConnectionWideCache(TypedKey<Map<K, V>> key) {
      throw new UnsupportedOperationException();
    }

    public boolean isCommentPrivacyAccessible() {
      return false;
    }

    public void setCommentPrivacyAccessible(boolean accessible, String source) {
    }
  }
}
