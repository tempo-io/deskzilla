package com.almworks.bugzilla.provider;

import com.almworks.api.engine.*;
import com.almworks.spi.provider.util.ServerSyncPoint;
import com.almworks.util.model.ScalarModel;
import org.jetbrains.annotations.*;

import java.util.Map;

public interface SynchroState {
  void update(SyncParameters parameters);

  SyncParameters extractParameters();

  void setSyncPoint(ServerSyncPoint syncPoint);

  @Nullable
  ServerSyncPoint getSyncPoint();

  Map<Integer, SyncType> extractPendingBugs();

  void setSyncState(SyncTask.State state);

  ScalarModel<SyncTask.State> getSyncStateModel();
}
