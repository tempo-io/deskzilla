package com.almworks.universe.optimize;

import com.almworks.api.universe.Particle;

import java.io.InputStream;

class PBytes12Key8 extends PBytesRowOptimized {
  private final long myKey;
  private final int myValue;

  public PBytes12Key8(long key, int value) {
    myKey = key;
    myValue = value;
  }

  public long getKey() {
    return myKey;
  }

  public byte[] raw() {
    byte[] array = new byte[12];
    ByteArrayAccessUtil.setLong(array, 0, myKey);
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
          return (int)((myKey >>> 56) & 0xFF);
        case 1:
          return (int)((myKey >>> 48) & 0xFF);
        case 2:
          return (int)((myKey >>> 40) & 0xFF);
        case 3:
          return (int)((myKey >>> 32) & 0xFF);
        case 4:
          return (int)((myKey >>> 24) & 0xFF);
        case 5:
          return (int)((myKey >>> 16) & 0xFF);
        case 6:
          return (int)((myKey >>> 8) & 0xFF);
        case 7:
          return (int)(myKey & 0xFF);
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
    if (p instanceof PBytes12Key8) {
      PBytes12Key8 that = ((PBytes12Key8) p);
      return myKey == that.myKey && myValue == that.myValue;
    } else {
      return super.fastEquals(p);
    }
  }

  public String toString() {
    return "PB12-8[" + myKey + "," + myValue + "]";
  }
}
