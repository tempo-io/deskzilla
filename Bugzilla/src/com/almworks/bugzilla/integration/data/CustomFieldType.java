package com.almworks.bugzilla.integration.data;

public enum CustomFieldType {
  TEXT(false),
  CHOICE(true),
  LARGE_TEXT(false),
  MULTI_SELECT(true),
  DATE_TIME(false),
  BUG_ID(false), // since bugzilla 3.4
  UNKNOWN(false);

  private final boolean myEnumerable;

  private CustomFieldType(boolean enumerable) {
    myEnumerable = enumerable;
  }

  public boolean isEnumerable() {
    return myEnumerable;
  }
}
