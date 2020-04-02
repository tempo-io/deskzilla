package com.almworks.database;

import com.almworks.api.database.*;

public class CachingViewPolicy implements ViewFilteringPolicy {
  private final ViewFilteringPolicy myDelegate;

  public CachingViewPolicy(ViewFilteringPolicy delegate) {
    assert delegate != null;
    myDelegate = delegate;
  }

  public ArtifactView filter(AbstractArtifactView parent, Filter filter, WCN.Range range) {
    return new CachingArtifactView2(parent.getBasis(), myDelegate.filter(parent, filter, range));
  }
}
