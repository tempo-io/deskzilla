package com.almworks.spi.provider;

import com.almworks.api.connector.ConnectorStateStorage;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.*;
import com.almworks.api.http.FeedbackHandler;
import com.almworks.api.store.Store;
import com.almworks.api.store.StoreUtils;
import com.almworks.api.syncreg.SyncRegistry;
import com.almworks.spi.provider.util.BasicHttpAuthHandler;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.io.persist.*;
import com.almworks.util.model.BasicScalarModel;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.properties.Role;
import com.almworks.util.threads.Bottleneck;
import javolution.util.SimplifiedFastMap;
import org.almworks.util.Log;
import org.almworks.util.TypedKey;

import java.util.Map;

public abstract class BaseConnectionContext implements ConnectionContext {
  private final BasicScalarModel<ConnectionState> myConnectionState =
    BasicScalarModel.createWithValue(ConnectionState.INITIAL, true);

  protected final Connection myConnection;
  protected final ComponentContainer myContainer;

  protected final BasicScalarModel<InitializationState> myInitializationState =
    BasicScalarModel.createWithValue(null, true);

  protected String myLastInitializationError;
  protected final Store myStore;
  private static final String INIT_STATE_STORE_KEY = "initialized.state";

  protected final Bottleneck mySaveInitState = new Bottleneck(500, ThreadGate.LONG(
    new Object()), new Runnable() {
      public void run() {
        saveInitializationState();
      }
    });

  private final FeedbackHandler myDefaultAuthHandler;
  private final ConnectorStateStorage myConnectorStateStorage;

  protected final Map<TypedKey, Map> myConnectionWideCaches = new SimplifiedFastMap<TypedKey, Map>().setShared(true);

  public BaseConnectionContext(Connection connection, ComponentContainer container, Store store) {
    myConnection = connection;
    myContainer = container;
    myStore = store;
    myDefaultAuthHandler = container.instantiate(BasicHttpAuthHandler.class);
    myConnectorStateStorage = new StoreBasedConnectionStateStorage(store, "con.st.st");
  }

  public ScalarModel<ConnectionState> getState() {
    return myConnectionState;
  }

  protected ConnectorStateStorage getConnectorStateStorage() {
    return myConnectorStateStorage;
  }

  public boolean commitState(ConnectionState prevState, ConnectionState newState) {
    return myConnectionState.commitValue(prevState, newState);
  }

  public Connection getConnection() {
    return myConnection;
  }

  protected FeedbackHandler getFeedbackHandler() {
    return myDefaultAuthHandler;
  }

  public ComponentContainer getContainer() {
    return myContainer;
  }

  public ScalarModel<InitializationState> getInitializationState() {
    return myInitializationState;
  }

  protected void loadInitializationState() {
    Persistable<InitializationState> pstate = PersistableEnumerable.create(InitializationState.class);
    PersistableString perror = new PersistableString();
    PersistableContainer pall = PersistableContainer.create(new Persistable[]{pstate, perror});

    InitializationState state;

    // todo kludge: we have to force class initialization before restorePersistable 
    Log.debug("kludge:" + InitializationState.NOT_INITIALIZED);

    String lastError;
    if (StoreUtils.restorePersistable(myStore, INIT_STATE_STORE_KEY, pall)) {
      state = pstate.access();
      if (state == null) {
        state = InitializationState.NOT_INITIALIZED;
      } else if (state == InitializationState.INITIALIZING) {
        // strange - we are just loaded
        state = InitializationState.NOT_INITIALIZED;
      } else if (state == InitializationState.REINITIALIZING) {
        // strange - we are just loaded
        state = InitializationState.REINITIALIZATION_REQUIRED;
      }

      lastError = perror.access();
    } else {
      state = InitializationState.NOT_INITIALIZED;
      lastError = null;
    }
    myLastInitializationError = lastError;
    myInitializationState.setValue(state);
  }

  public String getLastInitializationError() {
    return myLastInitializationError;
  }

  public void start() {
    loadInitializationState();
  }

  public void stop() {
  }

  public void setInitializationInProgress() {
    if (!myInitializationState.commitValue(InitializationState.NOT_INITIALIZED, InitializationState.INITIALIZING)) {
      myInitializationState.commitValue(InitializationState.REINITIALIZATION_REQUIRED,
        InitializationState.REINITIALIZING);
    }
    mySaveInitState.run();
  }

  private void saveInitializationState() {
    Persistable<InitializationState> pstate = PersistableEnumerable.create(InitializationState.class);
    PersistableString perror = new PersistableString(myLastInitializationError);
    PersistableContainer pall = PersistableContainer.create(new Persistable[]{pstate, perror});
    pstate.set(myInitializationState.getValue());
    StoreUtils.storePersistable(myStore, INIT_STATE_STORE_KEY, pall);
  }

  public void setInitializationResult(boolean success, String error) {
    if (success) {
      myLastInitializationError = null;
      myInitializationState.setValue(InitializationState.INITIALIZED);
    } else {
      myLastInitializationError = error;
      if (!myInitializationState.commitValue(InitializationState.INITIALIZING, InitializationState.NOT_INITIALIZED))
        myInitializationState.commitValue(InitializationState.REINITIALIZING,
          InitializationState.REINITIALIZATION_REQUIRED);
    }
    mySaveInitState.run();
  }

  public void requestReinitialization() {
    myInitializationState.commitValue(InitializationState.INITIALIZED, InitializationState.REINITIALIZATION_REQUIRED);
    mySaveInitState.run();
  }

  protected void clearSyncRegistry() {
    SyncRegistry syncRegistry = getActor(SyncRegistry.ROLE);
    if (syncRegistry == null) return;
    syncRegistry.clearRegistryForConnection(getConnection());
  }

  public <T> T getActor(Role<T> role) {
    T actor = myContainer.getActor(role);
    assert actor != null : role;
    return actor;
  }

  public <K, V> Map<K, V> getConnectionWideCache(TypedKey<Map<K, V>> key) {
    Map result = myConnectionWideCaches.get(key);
    if (result == null) {
      synchronized(myConnectionWideCaches) {
        result = myConnectionWideCaches.get(key);
        if (result == null) {
          result = new SimplifiedFastMap().setShared(true);
          myConnectionWideCaches.put(key, result);
        }
      }
    }
    return (Map<K,V>) result;
  }
}
