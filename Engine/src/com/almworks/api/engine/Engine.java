package com.almworks.api.engine;

import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.items.api.DP;
import com.almworks.items.dp.DPNotNull;
import com.almworks.items.util.DBNamespace;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.properties.Role;
import org.jetbrains.annotations.*;

public interface Engine {
  Role<Engine> ROLE = Role.role("Engine");
  DBNamespace NS = DBNamespace.moduleNs("com.almworks.engine");
  /**
   * All database queries filter this query. If you are sure that you don't need it, use {@link com.almworks.engine.items.DatabaseUnwrapper DatabaseUnwrapper}.
   * */
  BoolExpr<DP> VALID_ITEM = DPNotNull.create(SyncAttributes.EXISTING);

  ConnectionManager getConnectionManager();

  @NotNull
  Synchronizer getSynchronizer();

  EngineViews getViews();

  void registerGlobalDescriptor(ConstraintDescriptor descriptor);

  AListModel<ConstraintDescriptor> getGlobalDescriptors();
}
