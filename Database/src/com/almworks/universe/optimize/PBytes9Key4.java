package com.almworks.universe.optimize;

import com.almworks.api.universe.Particle;

import java.io.InputStream;

class PBytes9Key4 extends PBytesRowOptimized {
  private final int myKey;
  private final byte myValue;

  public PBytes9Key4(int key, byte value) {
    myKey = key;
    myValue = value;
  }

  public long getKey() {
    return ((long)myKey) & 0xFFFFFFFFL;
  }

  public byte[] raw() {
    byte[] array = new byte[9];
    ByteArrayAccessUtil.setInteger(array, 0, 0);
    ByteArrayAccessUtil.setInteger(array, 4, myKey);
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
          return ((int) myValue) & 0xFF;
        default:
          assert false : position;
          return -1;
        }
      }
    };
  }

  protected boolean fastEquals(Particle p) {
    if (p instanceof PBytes9Key4) {
      PBytes9Key4 that = (PBytes9Key4) p;
      return myKey == that.myKey && myValue == that.myValue;
    } else {
      return super.fastEquals(p);
    }
  }

  public String toString() {
    return "PB9-4[" + myKey + "," + myValue + "]";
  }
}
