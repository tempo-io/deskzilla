package com.almworks.database.bitmap;

import util.external.BitSet2;

abstract class BINodeOperation {
  protected abstract BitSet2 consume(BitSet2 runningBits, BitSet2 newBits, BitSet2 mask);

  public abstract BitSet2 finish(BitSet2 runningBits, BitSet2 mask);
}
