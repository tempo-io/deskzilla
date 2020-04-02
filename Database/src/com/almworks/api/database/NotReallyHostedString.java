package com.almworks.api.database;

public final class NotReallyHostedString extends HostedString {
  public NotReallyHostedString(String value) {
    myCachedString = value == null ? NULL_SENTINEL : value;
  }

  protected String loadString() {
    assert false : this;
    return myCachedString;
  }
}
