package com.almworks.database.bitmap;

import com.almworks.database.filter.FilterType;
import util.external.BitSet2;

public class BINodeLeaf extends BINode {
  private final AbstractBitmapIndex myLeafIndex;

  public BINodeLeaf(AbstractBitmapIndex index) {
    super(FilterType.LEAF);
    assert index != null;
    myLeafIndex = index;
  }

  public BitSet2 getBits(BitSet2 mask) throws InterruptedException {
    return myLeafIndex.getAtomBitsForReading();
  }

  public boolean canApplyQuick() {
    return true;
  }

  public BitSet2 applyQuick(BitSet2 sourceSet) throws InterruptedException {
    return myLeafIndex.applyImmediatelyTo(sourceSet);
  }

  public AbstractBitmapIndex getIndex() {
    return myLeafIndex;
  }
}
