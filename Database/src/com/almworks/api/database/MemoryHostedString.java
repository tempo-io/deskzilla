package com.almworks.api.database;

import com.almworks.universe.optimize.UniverseMemoryOptimizer;

public final class MemoryHostedString extends BytesHostedString {
  public MemoryHostedString(int offset, int length) {
    super(offset, length);
  }

  protected byte[] loadBytes() {
    return UniverseMemoryOptimizer.copy(myOffset, myLength);
  }

  public String toString() {
    return "MHS[" + myOffset + "," + myLength + "," + myCachedString + "]";
  }
}
