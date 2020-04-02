package com.almworks.api.engine;

import com.almworks.items.api.*;
import com.almworks.util.bool.BoolExpr;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface EngineViews {
  DBFilter getItemsOfType(DBItemType type);

  BoolExpr<DP> getPrimaryItemsFilter();

  BoolExpr<DP> getLocalChangesFilter();

  BoolExpr<DP> getConflictsFilter();
}
