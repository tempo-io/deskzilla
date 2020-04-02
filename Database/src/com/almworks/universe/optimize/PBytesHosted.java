package com.almworks.universe.optimize;

import com.almworks.api.universe.Particle;

import java.io.InputStream;

public class PBytesHosted extends Particle {
  private final int myOffset;
  private final int myLength;

  public PBytesHosted(int offset, int length) {
    myOffset = offset;
    myLength = length;
  }

  public byte[] raw() {
    return UniverseMemoryOptimizer.copy(myOffset, myLength);
  }

  public int getByteLength() {
    return myLength;
  }

  protected boolean fastEquals(Particle that) {
    if (that == null)
      return false;
    if (that == this)
      return true;
    if (myLength != that.getByteLength())
      return false;
    if (myLength == 0)
      return true;
    if (that instanceof PBytesHosted) {
      PBytesHosted thatHosted = ((PBytesHosted) that);
      return UniverseMemoryOptimizer.compareChunks(myOffset, thatHosted.myOffset, myLength);
    } else {
      byte[] thatArray = that.raw();
      if (thatArray.length != myLength) {
        assert false : this + " " + that;
        return false;
      }
      return UniverseMemoryOptimizer.compareArray(thatArray, myOffset);
    }
  }

  public InputStream getStream() {
    return new BytesRowStream(myLength) {
      protected int getByte(int position) {
        if (position >= 0 && position < myLength) {
          return ((int)UniverseMemoryOptimizer.getByte(myOffset + position)) & 0xFF;
        } else {
          return -1;
        }
      }
    };
  }

  public String toString() {
    return "PH[" + myOffset + ":" + myLength + "]";
  }

  public int getHostedOffset() {
    return myOffset;
  }
}
