package com.almworks.universe.optimize;

import com.almworks.api.universe.Particle;

import java.io.InputStream;

class PBytes16Key8Value8 extends PBytesRowOptimized {
  private final long myKey;
  private final long myValue;

  public PBytes16Key8Value8(long key, long value) {
    myKey = key;
    myValue = value;
  }

  public long getKey() {
    return myKey;
  }

  public byte[] raw() {
    byte[] array = new byte[16];
    ByteArrayAccessUtil.setLong(array, 0, myKey);
    ByteArrayAccessUtil.setLong(array, 8, myValue);
    return array;
  }

  public int getByteLength() {
    return 16;
  }

  public InputStream getStream() {
    return new BytesRowStream(16) {
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
          return (int)((myValue >>> 56) & 0xFF);
        case 9:
          return (int)((myValue >>> 48) & 0xFF);
        case 10:
          return (int)((myValue >>> 40) & 0xFF);
        case 11:
          return (int)((myValue >>> 32) & 0xFF);
        case 12:
          return (int)((myValue >>> 24) & 0xFF);
        case 13:
          return (int)((myValue >>> 16) & 0xFF);
        case 14:
          return (int)((myValue >>> 8) & 0xFF);
        case 15:
          return (int)(myValue & 0xFF);
        default:
          assert false : position;
          return -1;
        }
      }
    };
  }

  protected boolean fastEquals(Particle p) {
    if (p instanceof PBytes16Key8Value8) {
      PBytes16Key8Value8 that = (PBytes16Key8Value8) p;
      return myKey == that.myKey && myValue == that.myValue;
    } else {
      return super.fastEquals(p);
    }
  }

  public String toString() {
    return "PB16-8-8[" + myKey + "," + myValue + "]";
  }


}
