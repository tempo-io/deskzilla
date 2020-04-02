package com.almworks.api.universe;

import com.almworks.universe.optimize.UniverseFileOptimizer;
import com.almworks.util.NamedLong;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

/**
 * :todoc:
 *
 * @author sereda
 */
public abstract class Particle {
  public abstract byte[] raw();

  public abstract int getByteLength();

  public static Particle createIsoString(String string) {
    return new PIsoString(string);
  }

  public static Particle createLong(NamedLong value) {
    return createLong(value.getLong());
  }

  public static Particle createLong(long value) {
    return new PLong(value);
  }

  public static Particle createBytes(byte[] bytes) {
    return new PBytes(bytes);
  }

  public static Particle createEmpty() {
    return PEmpty.INSTANCE;
  }

  public static Particle create(Object object) {
    if (object instanceof Particle)
      return (Particle) object;
    if (object instanceof Atom)
      return createLong(((Atom) object).getAtomID());
    if (object instanceof String)
      return createIsoString((String) object);
    if (object instanceof Long)
      return createLong((Long) object);
    if (object == null)
      return createEmpty();
    if (object instanceof byte[])
      return new PBytes((byte[]) object);
    throw new IllegalArgumentException("cannot accept object " + object + " of class " + object.getClass());
  }

  public InputStream getStream() {
    return new ByteArrayInputStream(raw());
  }

  public final int hashCode() {
    // todo - this is very cpu-consuming method, yet we don't use hashCode() for particles much
    int result = 0;
    byte[] array = raw();
    for (int i = 0; i < array.length; i++)
      result = result * 23 + array[i];
    return result;
  }

  public final boolean equals(Object o) {
    if (!(o instanceof Particle))
      return false;
    Particle p = (Particle) o;
    if (getByteLength() != p.getByteLength())
      return false;
    return fastEquals(p);
  }

  protected boolean fastEquals(Particle p) {
    // not so fast - overwrite
    return Arrays.equals(raw(), p.raw());
  }

  public String toString() {
    return getClass().getName() + "[" + getByteLength() + "]";
  }

  private static byte[] gatherLongBytes(byte[] array, int offset, long value) {
    array[offset++] = (byte) ((value >>> 56) & 0xFF);
    array[offset++] = (byte) ((value >>> 48) & 0xFF);
    array[offset++] = (byte) ((value >>> 40) & 0xFF);
    array[offset++] = (byte) ((value >>> 32) & 0xFF);
    array[offset++] = (byte) ((value >>> 24) & 0xFF);
    array[offset++] = (byte) ((value >>> 16) & 0xFF);
    array[offset++] = (byte) ((value >>> 8) & 0xFF);
    array[offset] = (byte) (value & 0xFF);
    return array;
  }

  public static final class PEmpty extends Particle {
    private static final byte[] EMPTY = {};
    public static final Particle INSTANCE = new PEmpty();

    public byte[] raw() {
      return EMPTY;
    }

    public int getByteLength() {
      return 0;
    }

    protected boolean fastEquals(Particle p) {
      return true;
    }

    public String toString() {
      return "[E]";
    }
  }


  public static abstract class PRawCached extends Particle {
    private byte[] myRawCache;

    public synchronized final byte[] raw() {
      if (myRawCache == null)
        myRawCache = gatherBytes();
      return myRawCache;
    }

    protected abstract byte[] gatherBytes();
  }


  public static final class PLong extends Particle /*PRawCached */ {
    private final long myValue;

    public PLong(long value) {
      myValue = value;
    }

    public int getByteLength() {
      return 8;
    }

    public byte[] raw() {
      return gatherLongBytes(new byte[8], 0, myValue);
    }

    protected boolean fastEquals(Particle p) {
      if (p instanceof PLong)
        return myValue == ((PLong) p).myValue;
      else
        return super.fastEquals(p);
    }

    public long getValue() {
      return myValue;
    }

    public String toString() {
      return "L:" + myValue;
    }
  }


  public static final class PIsoString extends PRawCached {
    private final String myValue;

    public PIsoString(String value) {
      assert value != null;
      myValue = value;
    }

    protected byte[] gatherBytes() {
      return myValue.getBytes();
    }

    public int getByteLength() {
      return myValue.length();
    }

    protected boolean fastEquals(Particle p) {
      if (p instanceof PIsoString)
        return myValue.equals(((PIsoString) p).myValue);
      else
        return super.fastEquals(p);
    }

    public String getValue() {
      return myValue;
    }

    public String toString() {
      return "S:" + myValue;
    }
  }


  public static final class PBytes extends Particle {
    private final byte[] myBytes;

    public PBytes(byte[] bytes) {
      assert bytes != null;
      myBytes = bytes;
    }

    public byte[] raw() {
      return myBytes;
    }

    public int getByteLength() {
      return myBytes.length;
    }

    public String toString() {
      return "A:[" + myBytes.length + "]";
    }
  }

  public static final class PFileHostedBytes extends Particle {
    /**
     * First 8 bytes, read into long. Long must fit int :)
     */
    private final int myFirstLong;

    /**
     * File offset with first long skipped
     */
    private final int myOffset;

    /**
     * File length, without first long
     */
    private final int myLength;

    public PFileHostedBytes(int firstLong, int offset, int length) {
      myFirstLong = firstLong;
      myOffset = offset;
      myLength = length;
    }

    public byte[] raw() {
      byte[] result = new byte[myLength + 8];
      gatherLongBytes(result, 0, myFirstLong);
      UniverseFileOptimizer.readInto(myOffset, myLength, result, 8);
      return result;
    }

    public int getByteLength() {
      return myLength + 8;
    }

    public int getFirstLong() {
      return myFirstLong;
    }

    public int getOffset() {
      return myOffset;
    }

    public int getLength() {
      return myLength;
    }

    public byte[] rawHosted() {
      byte[] result = new byte[myLength];
      UniverseFileOptimizer.readInto(myOffset, myLength, result, 0);
      return result;
    }
  }
}
