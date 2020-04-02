package com.almworks.universe.optimize;

import com.almworks.api.universe.Particle;

import java.io.InputStream;

class PBytes12Key4 extends PBytesRowOptimized {
  private final int myKey;
  private final int myValue;

  public PBytes12Key4(int key, int value) {
    myKey = key;
    myValue = value;
  }

  public long getKey() {
    return ((long)myKey) & 0xFFFFFFFFL;
  }

  public byte[] raw() {
    byte[] array = new byte[12];
    ByteArrayAccessUtil.setInteger(array, 0, 0);
    ByteArrayAccessUtil.setInteger(array, 4, myKey);
    ByteArrayAccessUtil.setInteger(array, 8, myValue);
    return array;
  }

  public int getByteLength() {
    return 12;
  }

  public InputStream getStream() {
    return new BytesRowStream(12) {
      protected int getByte(int position) {
        switch (position) {
        case 0:
        case 1:
        case 2:
        case 3:
          return 0;
        case 4:
          return (myKey >>> 24) & 0xFF;
        case 5:
          return (myKey >>> 16) & 0xFF;
        case 6:
          return (myKey >>> 8) & 0xFF;
        case 7:
          return myKey & 0xFF;
        case 8:
          return (myValue >>> 24) & 0xFF;
        case 9:
          return (myValue >>> 16) & 0xFF;
        case 10:
          return (myValue >>> 8) & 0xFF;
        case 11:
          return myValue & 0xFF;
        default:
          assert false : position;
          return -1;
        }
      }
    };
  }

  protected boolean fastEquals(Particle p) {
    if (p instanceof PBytes12Key4) {
      PBytes12Key4 that = ((PBytes12Key4) p);
      return myKey == that.myKey && myValue == that.myValue;
    } else {
      return super.fastEquals(p);
    }
  }

  public String toString() {
    return "PB12-4[" + myKey + "," + myValue + "]";
  }
}
