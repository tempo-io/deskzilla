package com.almworks.api.application.tree;

import com.almworks.api.application.ItemKeyGroup;
import org.jetbrains.annotations.*;

public interface DistributionGroupNode extends GenericNode {
  @Nullable
  ItemKeyGroup getGroup();
}
