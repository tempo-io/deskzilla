package com.almworks.universe;

import com.almworks.api.universe.IndexInfo;
import com.almworks.api.universe.Universe;

public class IndexFactory {
  public static IndexInternal createIndex(State state, IndexInfo indexInfo) {
    return new IndexImpl2(state, indexInfo, Universe.BIG_BANG, Universe.END_OF_THE_UNIVERSE);
  }
}
