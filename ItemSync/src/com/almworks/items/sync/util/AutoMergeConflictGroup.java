package com.almworks.items.sync.util;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.*;
import org.almworks.util.ArrayUtil;

import java.util.Collection;

public class AutoMergeConflictGroup implements ItemAutoMerge {
  private final DBAttribute<?>[] myAttributes;

  public AutoMergeConflictGroup(DBAttribute<?>[] attributes) {
    myAttributes = ArrayUtil.arrayCopy(attributes);
  }

  @Override
  public void preProcess(ModifiableDiff local) {
    Collection<? extends DBAttribute<?>> changes = local.getChanged();
    boolean groupChanged = false;
    for (DBAttribute<?> attribute : myAttributes) {
      if (changes.contains(attribute)) {
        groupChanged = true;
        break;
      }
    }
    if (groupChanged) local.addChange(myAttributes);
  }

  @Override
  public void resolve(AutoMergeData data) {}
}
