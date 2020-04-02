package com.almworks.bugzilla.provider;

import com.almworks.api.engine.SyncParameters;
import com.almworks.spi.provider.AbstractConnectionInitializer;
import com.almworks.spi.provider.AbstractSyncTask;
import com.almworks.util.commons.Procedure2;

public class BugzillaConnectionInitializer extends AbstractConnectionInitializer<BugzillaContext> {
  private final BugzillaSynchronizer mySynchronizer;

  public BugzillaConnectionInitializer(BugzillaContext context, BugzillaSynchronizer synchronizer) {
    super(context);
    assert synchronizer != null;
    mySynchronizer = synchronizer;
  }

  protected AbstractSyncTask getSyncTask() {
    return mySynchronizer;
  }

  protected boolean startInitialization(Procedure2<Boolean, String> runOnFinish) {
    SyncParameters parameters = SyncParameters.initializeConnection(myContext.getConnection());
    return mySynchronizer.doSynchronize(parameters, runOnFinish);
  }
}
