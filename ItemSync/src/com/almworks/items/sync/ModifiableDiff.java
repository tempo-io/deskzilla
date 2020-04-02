package com.almworks.items.sync;

import com.almworks.items.api.DBAttribute;

public interface ModifiableDiff extends ItemDiff {
  void clearHistory();

  void addChange(DBAttribute<?>... attributes);
}
