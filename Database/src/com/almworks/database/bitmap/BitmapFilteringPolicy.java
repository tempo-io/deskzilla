package com.almworks.database.bitmap;

import com.almworks.api.database.*;
import com.almworks.database.AbstractArtifactView;
import com.almworks.database.ViewFilteringPolicy;
import com.almworks.database.filter.SystemFilter;

public class BitmapFilteringPolicy implements ViewFilteringPolicy {

//private static final DebugDichotomy __use = new DebugDichotomy("bitmap", "ordinary", 10);

  public ArtifactView filter(AbstractArtifactView parent, Filter filter, WCN.Range range) {
    if ((parent instanceof ViewHavingBitmap) && SystemFilter.isBitmapIndexable(filter)) {
//      __use.a();
      return new BitmapFilteredArtifactView(parent.getBasis(), parent, filter, range);
    } else {
//      __use.b();
      return parent.getBackupFilteringPolicy().filter(parent, filter, range);
    }
  }
}
