package com.almworks.engine;

import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.*;
import com.almworks.api.store.Store;
import com.almworks.items.api.Database;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.threads.ThreadAWT;
import org.jetbrains.annotations.*;
import org.picocontainer.Startable;

public class EngineImpl implements Engine, Startable {
  private final ConnectionManagerImpl myConnectionManager;
  private final EngineViews myViews;
  private final ItemProvider[] myProviders;
  private final ComponentContainer myContainer;
  private final SynchronizerImpl mySynchronizer;

  private final OrderListModel<ConstraintDescriptor> myGlobalDescriptors = OrderListModel.create();

  public EngineImpl(ItemProvider[] providers, ComponentContainer container, Store store, Database db) {
    assert providers != null;
    myProviders = providers;
    myContainer = container.createSubcontainer("engine");
    myViews = new EngineViewsImpl(providers, db);
    myConnectionManager = myContainer.instantiate(ConnectionManagerImpl.class);
    myConnectionManager.start();
    mySynchronizer = new SynchronizerImpl(myConnectionManager.getConnections(), store);
  }

  public ConnectionManager getConnectionManager() {
    return myConnectionManager;
  }

  @NotNull
  public Synchronizer getSynchronizer() {
    return mySynchronizer;
  }

  public EngineViews getViews() {
    return myViews;
  }

  @ThreadAWT
  public void registerGlobalDescriptor(ConstraintDescriptor descriptor) {
    myGlobalDescriptors.addElement(descriptor);
  }

  public AListModel<ConstraintDescriptor> getGlobalDescriptors() {
    return myGlobalDescriptors;
  }

  public void start() {
    if (myProviders != null)
      myConnectionManager.configureProviders(myProviders);
    myConnectionManager.loadConnections();
    mySynchronizer.start();
  }

  public void stop() {
  }
}

