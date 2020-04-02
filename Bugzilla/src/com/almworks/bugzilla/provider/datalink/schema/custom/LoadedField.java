package com.almworks.bugzilla.provider.datalink.schema.custom;

import com.almworks.items.api.DBAttribute;
import org.jetbrains.annotations.*;

public class LoadedField {
  private final String myId;
  private final String myName;
  private final long myFieldItem;
  private final long myAttrItem;
  private final DBAttribute<?> myAttribute;

  public LoadedField(String id, String name, long fieldItem, DBAttribute<?> attribute, long attrItem) {
    myId = id;
    myName = name;
    myFieldItem = fieldItem;
    myAttribute = attribute;
    myAttrItem = attrItem;
  }

  public DBAttribute<?> getAttribute() {
    return myAttribute;
  }

  public long getAttrItem() {
    return myAttrItem;
  }

  public long getFieldItem() {
    return myFieldItem;
  }

  public String getId() {
    return myId;
  }

  public String getName() {
    return myName;
  }

  @Nullable
  public static LoadedField create(String id, String name, long fieldItem, DBAttribute<?> attribute, Long attrItem) {
    if (id == null) return null;
    if (name == null) name = id;
    return new LoadedField(id, name, fieldItem, attribute, attrItem);
  }
}
