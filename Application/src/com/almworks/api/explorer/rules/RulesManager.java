package com.almworks.api.explorer.rules;

import com.almworks.api.application.qb.FilterNode;
import com.almworks.api.engine.Connection;
import com.almworks.util.properties.Role;
import org.jetbrains.annotations.*;

/**
 * @author dyoma
 */
public interface RulesManager {
  Role<RulesManager> ROLE = Role.role(RulesManager.class);

  @Nullable
  ResolvedRule getResolvedRule(@NotNull Connection connection, FilterNode stateKey);
}
