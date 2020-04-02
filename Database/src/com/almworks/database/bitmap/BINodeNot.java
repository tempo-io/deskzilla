package com.almworks.database.bitmap;

import util.external.BitSet2;

class BINodeNot extends BINodeOr {
  public static final BINodeNot INSTANCE = new BINodeNot();

  public BitSet2 finish(BitSet2 runningBits, BitSet2 mask) {
    BitSet2 result = (BitSet2)mask.clone();
    result.andNot(runningBits);
    return result;
  }
}
