package com.almworks.database;

import com.almworks.api.database.*;

public class DumbFilteringPolicy implements ViewFilteringPolicy {
  public static final DumbFilteringPolicy INSTANCE = new DumbFilteringPolicy();

  public ArtifactView filter(AbstractArtifactView parent, Filter filter, WCN.Range range) {
    return new FilteredArtifactView(parent.myBasis, parent, filter, range);
  }

  private DumbFilteringPolicy() {
  }
}
