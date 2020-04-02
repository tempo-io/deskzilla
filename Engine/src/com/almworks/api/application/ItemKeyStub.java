package com.almworks.api.application;

import org.jetbrains.annotations.*;

public class ItemKeyStub extends ItemKey {
  public static final ItemKeyStub ABSENT = new ItemKeyStub("");

  private final String myId;
  private final String myDisplayName;
  private final ItemOrder myOrder;

  public ItemKeyStub(String id, String displayName, ItemOrder order) {
    myId = id == null ? id : id.intern();
    displayName = hackFixHtmlValueName(displayName);
    myDisplayName = displayName == null ? displayName : displayName.intern();
    myOrder = order;
  }

  public ItemKeyStub(ItemKey value) {
    this(value.getId(), value.getDisplayName(), value.getOrder());
  }

  public ItemKeyStub(String id) {
    this(id, id, ItemOrder.byString(id));
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  @NotNull
  public ItemOrder getOrder() {
    return myOrder;
  }
}
