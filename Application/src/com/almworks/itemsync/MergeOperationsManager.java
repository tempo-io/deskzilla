package com.almworks.itemsync;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBItemType;
import com.almworks.items.sync.ItemAutoMerge;
import com.almworks.util.properties.Role;

import java.util.Collection;

public interface MergeOperationsManager {
  Role<MergeOperationsManager> ROLE = Role.role(MergeOperationsManager.class);
  
  void addMergeOperation(ItemAutoMerge operations, DBItemType... types);

  Builder buildOperation(DBItemType type);

  interface Builder {
    void finish();

    void uniteSetValues(DBAttribute<? extends Collection<? extends Long>> ... attributes);

    Builder discardEdit(DBAttribute<?>... attributes);

    void addCustom(ItemAutoMerge merge);

    Builder mergeLongSets(DBAttribute<? extends Collection<? extends Long>>... attributes);

    void mergeStringSets(DBAttribute<? extends Collection<? extends String>> attribute);

    void addConflictGroup(DBAttribute<?> ... attrGroup);
  }
}
