package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.engine.SyncProblem;
import org.jetbrains.annotations.*;

public interface SyncController {
  @NotNull
  SyncData getData();

  void runTask(Task task) throws CancelledException;

  void onIntegrationException(ConnectorException e);

  void onProblem(SyncProblem problem, BugBox box);

  /**
   * Remove problem which linked with specified item
   *
   * @param clearSeriousProblem if is <code>true</code>, any problem (serious or non-serious) will be removed, otherwise only non-serious problem will be removed  @see com.almworks.api.engine.SyncProblem#isSerious()
   */
  void noProblemsForItem(long item, boolean clearSeriousProblem);
}
