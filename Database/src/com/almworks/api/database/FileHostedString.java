package com.almworks.api.database;

import com.almworks.universe.optimize.UniverseFileOptimizer;

public final class FileHostedString extends BytesHostedString {
  public FileHostedString(int offset, int length) {
    super(offset, length);
    assert UniverseFileOptimizer.isActive() : this;
  }

  protected byte[] loadBytes() {
    assert UniverseFileOptimizer.isActive() : this;
    byte[] result = new byte[myLength];
    UniverseFileOptimizer.readInto(myOffset, myLength, result, 0);
    return result;
  }

  public String toString() {
    return "FHS[" + myOffset + "," + myLength + "," + myCachedString + "]";
  }
}
