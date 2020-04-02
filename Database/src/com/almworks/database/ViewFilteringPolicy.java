package com.almworks.database;

import com.almworks.api.database.*;

public interface ViewFilteringPolicy {
  ArtifactView filter(AbstractArtifactView parent, Filter filter, WCN.Range range);
}
