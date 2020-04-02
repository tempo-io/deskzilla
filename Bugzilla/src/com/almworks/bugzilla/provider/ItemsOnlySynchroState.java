package com.almworks.bugzilla.provider;

import com.almworks.api.engine.*;
import com.almworks.spi.provider.util.ServerSyncPoint;
import com.almworks.util.model.BasicScalarModel;
import com.almworks.util.model.ScalarModel;
import org.almworks.util.Collections15;

import java.util.Map;

class ItemsOnlySynchroState implements SynchroState {
  private final SyncParameters myParameters;
  private final BasicScalarModel<SyncTask.State> myState;

  public ItemsOnlySynchroState(SyncParameters parameters, BasicScalarModel<SyncTask.State> state) {
    myParameters = parameters;
    myState = state;
  }

  public SyncParameters extractParameters() {
    return myParameters;
  }

  public Map<Integer, SyncType> extractPendingBugs() {
    return Collections15.emptyMap();
  }

  public void setSyncPoint(ServerSyncPoint syncPoint) {
  }

  public ServerSyncPoint getSyncPoint() {
    return null;
  }

  public ScalarModel<SyncTask.State> getSyncStateModel() {
    return myState;
  }

  public void setSyncState(SyncTask.State state) {
    myState.setValue(state);
  }

  public void update(SyncParameters parameters) {
  }
}
