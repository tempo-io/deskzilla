package com.almworks.api.application.tree;

import com.almworks.api.engine.Connection;
import org.jetbrains.annotations.*;

public interface ConnectionLoadingNode extends GenericNode {
  @NotNull
  Connection getConnection();
}
