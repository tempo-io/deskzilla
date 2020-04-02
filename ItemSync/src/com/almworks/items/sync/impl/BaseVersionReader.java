package com.almworks.items.sync.impl;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBIdentifiedObject;
import com.almworks.items.sync.ItemVersion;
import org.jetbrains.annotations.*;

public abstract class BaseVersionReader extends BasicVersionSource implements ItemVersion {
  @NotNull
  public ItemVersion forItem(long item) {
    return BranchUtil.instance(getReader()).readItem(item, getBranch());
  }

  protected abstract Branch getBranch();

  @Override
  public boolean equalValue(DBAttribute<Long> attribute, DBIdentifiedObject object) {
    Long value = getValue(attribute);
    if (value == null || value <= 0) return false;
    long materialized = getReader().findMaterialized(object);
    return materialized == value;
  }

  @Override
  public <T> T getNNValue(DBAttribute<T> attribute, T nullValue) {
    T value = getValue(attribute);
    return value != null ? value : nullValue;
  }

  @Override
  public String toString() {
    return "VersionReader " + toStringItemInfo();
  }

  protected String toStringItemInfo() {
    long item = getItem();
    StringBuilder builder = new StringBuilder();
    builder.append("item=").append(item);
    if (item > 0) {
      Long type = DBAttribute.TYPE.getValue(item, getReader());
      builder.append(" type=").append(type);
      if (type != null && type > 0) builder.append(" (").append(DBAttribute.ID.getValue(type, getReader())).append(")");
    }
    return builder.toString();
  }
}
