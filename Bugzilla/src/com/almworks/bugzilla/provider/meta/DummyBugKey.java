package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.*;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.properties.PropertyMap;

/**
 * @author dyoma
 */ 
class DummyBugKey extends SystemKey<Boolean> {
  public DummyBugKey() {
    super("isDummy");
  }

  @Override
  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    ItemDownloadStage stage = ItemDownloadStage.getValue(itemVersion);
    boolean isDummy = ItemDownloadStage.DUMMY.equals(stage);
    values.put(getModelKey(), isDummy);
  }

  public void copyValue(ModelMap to, PropertyMap from) {
    super.copyValue(to, from);
    to.registerKey(getName(), this);
  }
}
