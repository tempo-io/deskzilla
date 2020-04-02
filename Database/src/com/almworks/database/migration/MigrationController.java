package com.almworks.database.migration;

import com.almworks.api.misc.WorkArea;
import com.almworks.universe.data.ExpansionInfo;
import com.almworks.universe.data.ExpansionInfoSink;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface MigrationController {
  void startMigration(WorkArea workArea) throws MigrationFailure;

  void makePass(ExpansionInfoSink sink) throws MigrationFailure;

  void saveExpansion(ExpansionInfo info);

  void endMigration() throws MigrationFailure;
}
