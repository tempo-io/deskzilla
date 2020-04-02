package com.almworks.database.bitmap;

import com.almworks.api.database.RevisionAccess;

public final class IndexKey {
  private final String myFilterKey;
  private final RevisionAccess myStrategy;

  public IndexKey(String filterKey, RevisionAccess strategy) {
    assert filterKey != null;
    assert strategy != null;
    myFilterKey = filterKey;
    myStrategy = strategy;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof IndexKey))
      return false;
    IndexKey that = (IndexKey) obj;
    return myFilterKey.equals(that.myFilterKey) && myStrategy.equals(that.myStrategy);
  }

  public int hashCode() {
    return myFilterKey.hashCode() * 113 + myStrategy.hashCode();
  }

  public String getIndexKey() {
    return myFilterKey + ":" + myStrategy.getName();
  }

  public RevisionAccess getStrategy() {
    return myStrategy;
  }

  public String toString() {
    return myFilterKey + ":" + myStrategy;
  }
}
