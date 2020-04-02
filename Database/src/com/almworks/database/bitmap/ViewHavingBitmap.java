package com.almworks.database.bitmap;

import util.external.BitSet2;


public interface ViewHavingBitmap {
  /**
   * Returns a bitset where index of a bit is atom id, and the value is true when revision (not artifact!)
   * is in view.
   */
  BitSet2 getRevisionBits() throws InterruptedException;

  void rebuildCorruptIndexes() throws InterruptedException;

  boolean hasAllIndices();
}
