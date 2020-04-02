package com.almworks.database.bitmap;

import util.external.BitSet2;

class BINodeAnd extends BINodeOperation {
  public static final BINodeAnd INSTANCE =  new BINodeAnd();

  protected BitSet2 consume(BitSet2 runningBits, BitSet2 newBits, BitSet2 mask) {
    if (runningBits == null) {
      return newBits;
    } else {
      return runningBits.modifiable().and(newBits);
    }
  }

  public BitSet2 finish(BitSet2 runningBits, BitSet2 mask) {
    return runningBits;
  }
}
