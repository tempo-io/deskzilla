package com.almworks.api.application;

import com.almworks.items.sync.ItemVersion;
import com.almworks.util.exec.Context;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.properties.Role;
import org.jetbrains.annotations.*;

public class ItemDownloadStageKey extends SystemKey<ItemDownloadStage> implements AutoAddedModelKey<ItemDownloadStage> {
  public static final Role<ItemDownloadStageKey> ROLE = Role.role(ItemDownloadStageKey.class);

  public ItemDownloadStageKey() {
    super("downloadStage");
  }

  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    ItemDownloadStage stage = ItemDownloadStage.getValue(itemVersion);
    values.put(getModelKey(), stage);
  }

  @NotNull
  public static ItemDownloadStage retrieveValue(PropertyMap values) {
    ItemDownloadStageKey key = Context.get(ROLE);
    if (key == null) {
      assert false : ROLE;
      return ItemDownloadStage.DEFAULT;
    }
    ItemDownloadStage value = key.getValue(values);
    if (value == null) {
      assert false : values;
      return ItemDownloadStage.DEFAULT;
    }
    return value;
  }

  public static ItemDownloadStage retrieveValue(ModelMap map) {
    ItemDownloadStageKey key = Context.get(ROLE);
    if (key == null) {
      assert false : ROLE;
      return ItemDownloadStage.DEFAULT;
    }
    ItemDownloadStage value = key.getValue(map);
    if (value == null) {
      assert false : map;
      return ItemDownloadStage.DEFAULT;
    }
    return value;
  }

  public static ItemDownloadStage retrieveValue(ItemWrapper wrapper) {
    return retrieveValue(wrapper.getLastDBValues());
  }
}



