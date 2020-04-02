package com.almworks.universe.optimize;

import com.almworks.api.universe.Particle;

import java.io.InputStream;

class PBytes9Key8 extends PBytesRowOptimized {
  private final long myKey;
  private final byte myValue;

  public PBytes9Key8(long key, byte value) {
    myKey = key;
    myValue = value;
  }

  public long getKey() {
    return myKey;
  }

  public byte[] raw() {
    byte[] array = new byte[9];
    ByteArrayAccessUtil.setLong(array, 0, myKey);
    array[8] = myValue;
    return array;
  }

  public int getByteLength() {
    return 9;
  }

  public InputStream getStream() {
    return new BytesRowStream(9) {
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
          return ((int) myValue) & 0xFF;
        default:
          assert false : position;
          return -1;
        }
      }
    };
  }
  protected boolean fastEquals(Particle p) {
    if (p instanceof PBytes9Key8) {
      PBytes9Key8 that = (PBytes9Key8) p;
      return myKey == that.myKey && myValue == that.myValue;
    } else {
      return super.fastEquals(p);
    }
  }

  public String toString() {
    return "PB9-8[" + myKey + "," + myValue + "]";
  }

}
