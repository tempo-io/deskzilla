package com.almworks.universe.optimize;

import com.almworks.api.universe.Particle;

import java.io.InputStream;

class PBytesRareCase extends PBytesRowOptimized {
  private final long myKey;
  private final long myValue;
  private final byte myValueLength;

  public PBytesRareCase(long key, byte valueLength, long value) {
    myKey = key;
    myValue = value;
    myValueLength = valueLength;
  }

  public long getKey() {
    return myKey;
  }

  public byte[] raw() {
    byte[] array = new byte[8 + myValueLength];
    ByteArrayAccessUtil.setLong(array, 0, myKey);
    int k = 0;
    for (int i = myValueLength - 1; i >= 0; i--) {
      array[8 + i] = (byte)((myValue >>> k) & 0xFF);
      k += 8;
    }
    return array;
  }

  public int getByteLength() {
    return 8 + myValueLength;
  }

  public InputStream getStream() {
    return new BytesRowStream(8 + myValueLength) {
      protected int getByte(int position) {
        switch (position) {
        case 0:
        case 1:
        case 2:
        case 3:
        case 4:
        case 5:
        case 6:
        case 7:
          return (int)((myKey >>> ((7 - position) * 8)) & 0xFF);
        case 8:
        case 9:
        case 10:
        case 11:
        case 12:
        case 13:
        case 14:
        case 15:
          return myValueLength > (position - 8) ? (int)((myValue >>> ((myValueLength - (position - 8) - 1) * 8)) & 0xFF) : -1;
        default:
          assert false : position;
          return -1;
        }
      }
    };
  }

  protected boolean fastEquals(Particle p) {
    if (p instanceof PBytesRareCase) {
      PBytesRareCase that = (PBytesRareCase) p;
      return myKey == that.myKey && myValue == that.myValue && myValueLength == that.myValueLength;
    } else {
      return super.fastEquals(p);
    }
  }

  public String toString() {
    return "PBR[" + myKey + "," + myValueLength + "," + myValue + "]";
  }
}
