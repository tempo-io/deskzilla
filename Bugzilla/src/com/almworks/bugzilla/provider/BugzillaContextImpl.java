package com.almworks.bugzilla.provider;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ResolvedItem;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.download.*;
import com.almworks.api.engine.*;
import com.almworks.api.gui.MainWindowManager;
import com.almworks.api.http.*;
import com.almworks.api.store.Store;
import com.almworks.api.store.StoreUtils;
import com.almworks.api.syncreg.*;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.data.BugzillaUser;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.items.api.*;
import com.almworks.spi.provider.BaseConnectionContext;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.commons.*;
import com.almworks.util.config.*;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.*;
import com.almworks.util.threads.CanBlock;
import org.almworks.util.*;
import org.almworks.util.detach.DetachComposite;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;
import util.concurrent.Synchronized;

import java.io.File;
import java.net.MalformedURLException;
import java.util.*;

import static org.almworks.util.Collections15.hashSet;

public class BugzillaContextImpl extends BaseConnectionContext implements BugzillaContext, DownloadOwner {
  private final ArrayListCollectionModel<RemoteQuery> myRemoteQueries = ArrayListCollectionModel.create(true, true);
  private final BasicScalarModel<OurConfiguration> myConfiguration = BasicScalarModel.create(true, true);
  private final CommonMetadata myMetadata;
  private final PrivateMetadata myPrivateMetadata;
  private final Views myViews;
  private final HttpClientProvider myHttpClientProvider;
  private final HttpLoaderFactory myHttpLoaderFactory;

  //private BugzillaIntegration myIntegration = null;
  private final Map<BugzillaAccessPurpose, BugzillaIntegration> myIntegrations = Collections15.hashMap();
  private volatile Factory<BugzillaIntegration> myIntegrationFactory;

  private final DetachComposite myDetach = new DetachComposite();

  private final BugzillaLearnedMeta myLearnedMeta;
  private final BugzillaCustomFields myCustomFields;

  private final WorkflowTracker myWorkflowTracker;
  private final ComponentDefaultsTracker myComponentDefaultsTracker;
  private final PermissionTracker myPermissionTracker;
  private final OptionalFieldsTracker myOptionalFieldsTracker;

  private final Set<String> myLastLimitingProducts = hashSet();
  private final Synchronized<Object> myIntegrationLocked = new Synchronized<Object>(null);
  private volatile String myBzVersion = null;

  BugzillaContextImpl(CommonMetadata metadata, PrivateMetadata privateMetadata,
    ComponentContainer container, BugzillaConnection connection, final Configuration configuration,
    HttpClientProvider httpClientProvider, HttpLoaderFactory httpLoaderFactory, Store store,
    DownloadManager downloadManager, MainWindowManager mainWindow)
  {
    super(connection, container, store);

    myMetadata = metadata;
    myPrivateMetadata = privateMetadata;
    myHttpClientProvider = httpClientProvider;
    myHttpLoaderFactory = httpLoaderFactory;
    myViews = new Views();
    myLearnedMeta = new BugzillaLearnedMeta(myStore);

    downloadManager.registerDownloadOwner(getDownloadOwner());

    setupRemoteQueries(configuration);

    myCustomFields = new BugzillaCustomFields(privateMetadata);
    myWorkflowTracker = new WorkflowTracker(myStore.getSubStore("wkf"));
    myComponentDefaultsTracker = new ComponentDefaultsTracker(myStore.getSubStore("cdft"));
    myPermissionTracker = new PermissionTracker(myStore.getSubStore("pt"));
    myOptionalFieldsTracker = new OptionalFieldsTracker(myStore.getSubStore("oft"));
  }

  private void setupRemoteQueries(final Configuration configuration) {
    final Configuration remoteQueriesConf = configuration.getOrCreateSubset("Remote");
    final List<Configuration> allSubsets = remoteQueriesConf.getAllSubsets("remoteQuery");
    for (int i = 0; i < allSubsets.size(); i++) {
      final Configuration subset = allSubsets.get(i);
      try {
        final RemoteQueryImpl remoteQuery =
          new RemoteQueryImpl(this, subset.getMandatorySetting("name"), subset.getMandatorySetting("url"));
        myRemoteQueries.getWritableCollection().add(remoteQuery);
      } catch (ReadonlyConfiguration.NoSettingException e) {
        Log.warn("Cannot build remote query from config", e);
      }
    }

    // total configuration rewriting when changes occured
    myRemoteQueries.getEventSource().addListener(ThreadGate.LONG(this), new CollectionModel.Adapter<RemoteQuery>() {
      protected void onChange() {
        remoteQueriesConf.clear();
        final Collection<RemoteQuery> remoteQueries = getRemoteQueries().copyCurrent();
        for (Iterator<RemoteQuery> iterator = remoteQueries.iterator(); iterator.hasNext();) {
          RemoteQuery remoteQuery = iterator.next();
          if (remoteQuery instanceof RemoteQueryImpl) {
            final RemoteQueryImpl remoteQueryImpl = (RemoteQueryImpl) remoteQuery;
            final Configuration subset = remoteQueriesConf.createSubset("remoteQuery");
            subset.setSetting("name", remoteQueryImpl.getQueryName());
            subset.setSetting("url", remoteQueryImpl.getQueryURL());
          }
        }
      }
    });
  }

  public BugzillaIntegration getIntegration(BugzillaAccessPurpose purpose) throws ConnectionNotConfiguredException {
    assert purpose != null;
    synchronized (myIntegrations) {
      BugzillaIntegration integration = myIntegrations.get(purpose);
      if (integration == null) {
        if (myIntegrationFactory == null)
          throw new ConnectionNotConfiguredException();
        integration = myIntegrationFactory.create();
        myIntegrations.put(purpose, integration);
      }
      assert integration != null;
      return integration;
    }
  }

  @NotNull
  public ScalarModel<OurConfiguration> getConfiguration() {
    return myConfiguration;
  }

  public CommonMetadata getMetadata() {
    return myMetadata;
  }

  public PrivateMetadata getPrivateMetadata() {
    return myPrivateMetadata;
  }

  public DBFilter getBugsView() {
    return myViews.getConnectionItems();
  }

  public ArrayListCollectionModel<RemoteQuery> getRemoteQueries() {
    return myRemoteQueries;
  }

  public ConnectionViews getViews() {
    return myViews;
  }

  @Override
  public BugzillaConnection getConnection() {
    return (BugzillaConnection) super.getConnection();
  }

  public void subscribeForConfigChanges() {
    myConfiguration.getEventSource().addListener(myDetach, ThreadGate.LONG(this), new ScalarModel.Adapter<OurConfiguration>() {
      public void onScalarChanged(ScalarModelEvent<OurConfiguration> event) {
        configurationChanged(event.getOldValue(), event.getNewValue());
      }
    });
  }

  private void configurationChanged(OurConfiguration oldConfig, OurConfiguration newConfig) {
    updatePrivateMetadata(oldConfig, newConfig);
    updateInitializationState(oldConfig, newConfig);
    updateTrackers(oldConfig, newConfig);
  }

  private void updateTrackers(OurConfiguration oldConfig, OurConfiguration newConfig) {
    if (oldConfig == null || newConfig == null)
      return;
    List<String> changed =
      ConfigurationUtil.getSettingsDifference(oldConfig.getConfiguration(), newConfig.getConfiguration());
    if (changed.contains(OurConfiguration.BASE_URL) || changed.contains(OurConfiguration.IS_ANONYMOUS_ACCESS) ||
      changed.contains(OurConfiguration.USER_NAME) || changed.contains(OurConfiguration.PASSWORD))
    {
      myWorkflowTracker.clearState();
      myComponentDefaultsTracker.clearState();
      myPermissionTracker.clearState();
    }
  }

  private void updateInitializationState(OurConfiguration oldConfig, OurConfiguration newConfig) {
    if (oldConfig != null && newConfig != null
      && areSeriouslyDifferent(oldConfig.getConfiguration(), newConfig.getConfiguration()))
    {
      requestReinitialization();
    }
  }

  private void updatePrivateMetadata(OurConfiguration oldConfig, OurConfiguration newConfig) {
    myPrivateMetadata.updateConfiguration(oldConfig, newConfig);
  }

  void configure(ReadonlyConfiguration configuration) throws ConfigurationException {
    assert configuration != null;
    OurConfiguration oldConfiguration = myConfiguration.isContentKnown() ? myConfiguration.getValue() : null;
    synchronized (myIntegrations) {
      closeIntegrations();
      OurConfiguration newConfiguration = new OurConfiguration(configuration);
      final String baseURL = newConfiguration.getBaseURL();
      final boolean anonymousAccess = newConfiguration.isAnonymousAccess();
      final String username = newConfiguration.getUsername();
      final String password = newConfiguration.getPassword();
      final String charset = newConfiguration.isCharsetSpecified() ? newConfiguration.getCharset() : "UTF-8";
      final String emailSuffix = newConfiguration.getEmailSuffixIfUsing();
      final boolean ignoreProxy = newConfiguration.isIgnoreProxy();
      final TimeZone timeZone = newConfiguration.getTimeZone();
      // create first integration to ensure that it is possible
      if (baseURL == null || baseURL.trim().length() == 0)
        throw new ConfigurationException("empty URL");
      try {
        BugzillaIntegration integration =
          createIntegration(baseURL, anonymousAccess, username, password, charset, ignoreProxy, timeZone, emailSuffix, myBzVersion);
        myIntegrations.put(BugzillaAccessPurpose.SYNCHRONIZATION, integration);
      } catch (MalformedURLException e) {
        throw new ConfigurationException("bad URL [" + baseURL + "]", e);
      }

      myIntegrationFactory = new Factory<BugzillaIntegration>() {
        public BugzillaIntegration create() {
          try {
            return createIntegration(baseURL, anonymousAccess, username, password, charset, ignoreProxy, timeZone, emailSuffix, myBzVersion);
          } catch (MalformedURLException e) {
            throw new Failure(e);
          }
        }
      };
      myConfiguration.setValue(newConfiguration);
    }
    if (oldConfiguration != null && areSeriouslyDifferent(oldConfiguration.getConfiguration(), configuration)) {
      clearSyncRegistry();
    }
    maybeUpdateLimitingProducts();
  }

  private boolean areSeriouslyDifferent(ReadonlyConfiguration originalConfig, ReadonlyConfiguration newConfig) {
    List<String> difference = ConfigurationUtil.getSettingsDifference(originalConfig, newConfig);
    for (int i = 0; i < difference.size(); i++) {
      String s = difference.get(i);
      if (s.equals(CommonConfigurationConstants.CONNECTION_NAME))
        continue;
      if (s.equals(OurConfiguration.BASE_URL)) {
        try {
          if (Util.equals(BugzillaIntegration.normalizeURL(originalConfig.getSetting(s, "")),
            BugzillaIntegration.normalizeURL(newConfig.getSetting(s, ""))))
            continue;
          else
            return true;
        } catch (MalformedURLException e) {
          return true;
        }
      } else
        return true;
    }
    return false;
  }

  private BugzillaIntegration createIntegration(final String baseURL, final boolean anonymousAccess,
    final String username, final String password, String charset, boolean ignoreProxy,
    @Nullable TimeZone defaultTimezone, @Nullable String emailSuffix, @Nullable String bzVersion) throws MalformedURLException
  {
    BugzillaAccountNameSink sink = new BugzillaAccountNameSink() {
      public void updateAccountName(final BugzillaUser accountName) {
        myPrivateMetadata.processAccountNameSuggestedByBugzilla(accountName);
      }
    };

    return BugzillaUtil.createIntegration(myHttpLoaderFactory, myHttpClientProvider, baseURL, anonymousAccess, username,
      password, getFeedbackHandler(), null, charset, sink, ignoreProxy, defaultTimezone, getConnectorStateStorage(),
      emailSuffix, bzVersion, new Procedure<String>() {
        @Override
        public void invoke(final String version) {
          if (version == null) return;
          myBzVersion = version;
          getActor(Database.ROLE).writeBackground(new WriteTransaction<Object>() {
            @Override
            public Object transaction(DBWriter writer) throws DBOperationCancelledException {
              writer.setValue(writer.materialize(getPrivateMetadata().getConnectionRef()),
                CommonMetadata.attrBugzillaVerison, version);
              return null;
            }
          });
        }
      });
  }

  void closeIntegrations() {
    final BugzillaIntegration[] integrations;
    synchronized (myIntegrations) {
      integrations = myIntegrations.values().toArray(new BugzillaIntegration[myIntegrations.size()]);
      myIntegrations.clear();
      myIntegrationFactory = null;
    }
    ThreadGate.LONG(this).execute(new Runnable() {
      public void run() {
        for (int i = 0; i < integrations.length; i++) {
          BugzillaIntegration integration = integrations[i];
          try {
            integration.close();
          } catch (Exception e) {
            Log.warn("error while closing unneeded integration", e);
          }
        }
      }
    });
  }

  private void maybeUpdateLimitingProducts() {
    Set<String> newLimitingProducts = hashSet(myConfiguration.getValue().getLimitingProducts());
    if (!myLastLimitingProducts.equals(newLimitingProducts)) {
      myLastLimitingProducts.clear();
      myLastLimitingProducts.addAll(newLimitingProducts);
      myViews.onLimitingProductsUpdated();
    }
  }

  public DownloadOwner getDownloadOwner() {
    return this;
  }

  public boolean isCommentPrivacyAccessible() {
    return myLearnedMeta.isCommentPrivacyAccessible();
  }

  public void setCommentPrivacyAccessible(boolean accessible, String source) {
    myLearnedMeta.setCommentPrivacyAccessible(accessible, source);
  }

  public HttpLoader createLoader(String argument, boolean retrying) throws CannotCreateLoaderException {
    try {
      int attachmentId = Integer.parseInt(argument);
      return getIntegration(BugzillaAccessPurpose.ATTACHMENT_DOWNLOAD).getAttachmentHttpLoader(attachmentId);
    } catch (NumberFormatException e) {
      throw new CannotCreateLoaderException("bad argument " + argument, e);
    } catch (ConnectionNotConfiguredException e) {
      throw new CannotCreateLoaderException(this + " is not configured", e);
    } catch (ConnectorException e) {
      throw new CannotCreateLoaderException(e.getMessage(), e);
    }
  }

  public boolean isValid() {
    return getState().getValue() == ConnectionState.READY;
  }

  public String getDownloadOwnerID() {
    return "bugzilla:" + myConnection.getConnectionID();
  }

  public void validateDownload(String argument, File downloadedFile, String mimeType) throws DownloadInvalidException {
    try {
      int attachmentId = Integer.parseInt(argument);
      BugzillaIntegration integration = getIntegration(BugzillaAccessPurpose.ATTACHMENT_DOWNLOAD);
      integration.validateAttachmentDownload(attachmentId, mimeType, downloadedFile);
    } catch (NumberFormatException e) {
      // ignore bad argument
    } catch (ConnectionNotConfiguredException e) {
      // ignore
      Log.debug("cannot validate ", e);
    } catch (ConnectorException e) {
      throw new DownloadInvalidException(e.getLongDescription(), false);
    }
  }

  public void start() {
    super.start();
    getActor(Database.ROLE).readForeground(new ReadTransaction<Object>() {
      @Override
      public Object transaction(DBReader reader) throws DBOperationCancelledException {
        long connection = reader.findMaterialized(getPrivateMetadata().getConnectionRef());
        if (connection > 0) {
          String bzVersion = reader.getValue(connection, CommonMetadata.attrBugzillaVerison);
          if (bzVersion != null) myBzVersion = bzVersion;
        }
        return null;
      }
    });
    subscribeForConfigChanges();
    myLearnedMeta.load();
    try {
      myCustomFields.start(Database.require());
    } catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
    myWorkflowTracker.start();
    myComponentDefaultsTracker.start();
    myPermissionTracker.start();
    myOptionalFieldsTracker.start();  
  }

  public void stop() {
    super.stop();
    closeIntegrations();
    myDetach.detach();
  }

  public Lifespan getConnectionLife() {
    return myDetach;
  }

  public BugzillaCustomFields getCustomFields() {
    return myCustomFields;
  }

  public WorkflowTracker getWorkflowTracker() {
    return myWorkflowTracker;
  }

  public PermissionTracker getPermissionTracker() {
    return myPermissionTracker;
  }

  public ComponentDefaultsTracker getComponentDefaultsTracker() {
    return myComponentDefaultsTracker;
  }

  @Override
  public OptionalFieldsTracker getOptionalFieldsTracker() {
    return myOptionalFieldsTracker;
  }

  @Override
  public void lockIntegration(Object owner) throws InterruptedException {
    if (owner == null) {
      Log.error("Missing owner. Lock ignored");
      return;
    }
    long start = System.currentTimeMillis();
    long waitNotifications = 0;
    int waitCountDown = 100;
    while (true) {
      myIntegrationLocked.waitForValue(null, 1000);
      if (myIntegrationLocked.commit(null, owner)) break;
      waitCountDown--;
      if (waitCountDown >= 0) continue;
      long waitPeriod = System.currentTimeMillis() - start;
      if (waitPeriod > 2 * Const.MINUTE) {
        Object prevOwner = myIntegrationLocked.get();
        if (prevOwner == null && myIntegrationLocked.commit(null, owner)) break;
        Log.warn("Integration lock ignored " + prevOwner);
        myIntegrationLocked.set(owner);
        break;
      }
      long notify = waitPeriod / (30 * Const.SECOND);
      if (notify > waitNotifications) {
        waitNotifications = notify;
        Log.warn("Integration still locked (" + waitPeriod / 1000 + " sec) by " + myIntegrationLocked.get());
      }
    }
  }

  @Override
  public void unlockIntegration(Object owner) {
    if (!myIntegrationLocked.commit(owner, null)) {
      Object current = myIntegrationLocked.get();
      if (current != owner) Log.warn("Integration owned by " + current);
    }
  }

  /**
   * @param hypercube
   * @return empty set if no product limitations are configured or not empty set with limiting products.
   * NOT thread safe: uses computation in AWT Thread and waits for results of computation
   */
  @NotNull
  public Set<Long> getLimitingProductsArtifacts(@Nullable ItemHypercube hypercube) {
    BaseEnumConstraintDescriptor productConstraintDescr = getMetadata().getEnumDescriptor(BugzillaAttribute.PRODUCT);
    List<ResolvedItem> productArtifacts = hypercube != null ? productConstraintDescr.getAllValues(hypercube) : productConstraintDescr.getAllValues(getConnection());
    Set<Long> limitingProducts = hashSet();
    String[] names = getConfiguration().getValue().getLimitingProducts();
    if (names.length == 0) return Collections15.emptySet();
    Set<String> limitingProductsNames = hashSet(names);
    for (ResolvedItem productArtifact : productArtifacts) {
      if(limitingProductsNames.contains(productArtifact.getId())) {
        limitingProducts.add(productArtifact.getResolvedItem());
      }
    }
    return limitingProducts;
  }

  /**
   * see also JiraContextImpl.getConfiguredProjectsArtifacts
   */
  // todo thread-safe cache of resolved enums
  public void getConfiguredProductsItems(@NotNull Lifespan contLife, @NotNull final Procedure<Set<Long>> cont) {
    if (contLife.isEnded()) return;

    String[] limitingProducts = getConfiguration().getValue().getLimitingProducts();
    final Set<String> limitingProductsNames = hashSet(limitingProducts);

    CommonMetadata md = getMetadata();
    BaseEnumConstraintDescriptor productsDescriptor = md.getEnumDescriptor(BugzillaAttribute.PRODUCT);
    if(productsDescriptor == null) {
      cont.invoke(Collections.EMPTY_SET);
      return;
    }

    final HashSet<Long> result = Collections15.hashSet();
    final int projectsNeeded = limitingProducts.length;

    final DetachComposite collectLife = new DetachComposite();
    contLife.add(collectLife);
    final Procedure<List<ItemKey>> update = new Procedure<List<ItemKey>>() {
      public void invoke(List<ItemKey> products) {
        for (ItemKey p : products) {
          if (limitingProductsNames.contains(p.getId())) {
            result.add(p.getResolvedItem());
          }
        }
        if (result.size() == projectsNeeded) {
          cont.invoke(result);
          // we've been called from a listener on "added" event; if we call detach right now, it will attempt to remove elements - while still processing "added" event
          ThreadGate.AWT_QUEUED.execute(new Runnable() {
            @Override
            public void run() {
              collectLife.detach();
            }
          });
        }
      }
    };

    ItemHypercube cube = ItemHypercubeUtils.adjustForConnection(new ItemHypercubeImpl(), getConnection());
    final AListModel<ItemKey> projectsModel = productsDescriptor.getResolvedEnumModel(collectLife, cube);
    update.invoke(projectsModel.toList());
    collectLife.add(projectsModel.addListener(new AListModel.Adapter<ItemKey>() {
      @Override
      public void onInsert(int index, int length) {
        update.invoke(projectsModel.subList(index, index + length));
      }

      @Override
      public void onItemsUpdated(AListModel.UpdateEvent event) {
        update.invoke(event.collectUpdated(projectsModel));
      }
    }));
  }

  private final class Views implements ConnectionViews {
    private final SimpleModifiable myConnectionArtifactsModifiable = new SimpleModifiable();

    private final Lazy<DBFilter> myOutbox = new Lazy<DBFilter>() {
      public DBFilter instantiate() {
        return getConnectionItems().filter(myConnection.getProvider().getPrimaryStructure().getLocallyChangedFilter());
      }
    };

    public Views() {
    }

    public DBFilter getConnectionItems() {
      return myPrivateMetadata.getBugsView();
    }

    public SimpleModifiable connectionItemsChange() {
      return myConnectionArtifactsModifiable;
    }

    public DBFilter getOutbox() {
      return myOutbox.get();
    }

    public void onLimitingProductsUpdated() {
      myConnectionArtifactsModifiable.fireChanged();
    }
  }

  private static class BugzillaLearnedMeta {
    private static final String STORE_ID = "learned";
    private final Store myStore;

    private static final String COMMENT_PRIVACY_ACCESSIBLE = "COMMENT_PRIVACY_ACCESSIBLE";
    private boolean myCommentPrivacyAccessible;

    public BugzillaLearnedMeta(Store store) {
      myStore = store;
    }

    @CanBlock
    public void load() {
      myCommentPrivacyAccessible = StoreUtils.restoreBoolean(myStore, STORE_ID, COMMENT_PRIVACY_ACCESSIBLE);
    }

    public void setCommentPrivacyAccessible(final boolean accessible, String source) {
      if (myCommentPrivacyAccessible != accessible) {
        Log.debug("comment privacy accessible changed to " + accessible + " because of " + source);
        myCommentPrivacyAccessible = accessible;
        ThreadGate.LONG_OPTIMAL.execute(new Runnable() {
          public void run() {
            StoreUtils.storeBoolean(myStore, STORE_ID, COMMENT_PRIVACY_ACCESSIBLE, accessible);
          }
        });
      }
    }

    public boolean isCommentPrivacyAccessible() {
      return myCommentPrivacyAccessible;
    }
  }
}
