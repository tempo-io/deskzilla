package com.almworks.util.collections;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Dyoma
 */
public class ByteArray {
  private byte[] myBytes;
  private int mySize = 0;

  public ByteArray() {
    this(10);
  }

  public ByteArray(int size) {
    this(new byte[size], 0);
  }

  public ByteArray(byte[] bytes, int size) {
    myBytes = bytes;
    mySize = size;
  }

  public static ByteArray wrap(byte[] bytes) {
    return new ByteArray(bytes, bytes.length);
  }

  /**
   *
   * @param stream an {@link InputStream} to read from
   * @param count maximum number of bytes to read
   * @return See {@link InputStream#read(byte[], int, int)}
   * @throws IOException
   */
  public int readFrom(InputStream stream, int count) throws IOException {
    ensureCapacity(mySize + count);
    int readBytes = stream.read(myBytes, mySize, count);
    if (readBytes > 0) mySize += readBytes;
    return readBytes;
  }

  public void readAllFromStream(InputStream stream) throws IOException {
    //noinspection StatementWithEmptyBody
    while (readFrom(stream, Math.max(1, stream.available())) >= 0);
  }

  public byte[] toNativeArray() {
    return getBytes(0, mySize);
  }

  public int size() {
    return mySize;
  }

  public void add(byte[] bytes) {
    ensureCapacity(mySize + bytes.length);
    setBytes(mySize, bytes, 0, bytes.length);
  }

  public void addLong(long value) {
    setLong(mySize, value);
  }

  public void addInt(int value) {
    setInt(mySize, value);
  }

  private void ensureCapacity(int expectedSize) {
    if (expectedSize <= myBytes.length) return;
    byte[] newBytes = new byte[Math.max(expectedSize, myBytes.length*2)];
    System.arraycopy(myBytes, 0, newBytes, 0, mySize);
    myBytes = newBytes;
  }

  public void copyBytes(int position, byte[] dest, int offset, int length) {
    System.arraycopy(myBytes, position, dest, offset, length);
  }

  public void setBytes(int pos, byte[] bytes, int offset, int length) {
    ensureCapacity(pos + length);
    System.arraycopy(bytes, offset, myBytes, pos, length);
    mySize = Math.max(mySize, pos + length);
  }

  public void setSize(int size) {
    if (mySize < size)
      ensureCapacity(size);
    mySize = size;
  }

  public long getLong(int offset) {
    if (offset + 8> mySize)
      throw new IndexOutOfBoundsException(offset + " " + mySize);
    return getLong(myBytes, offset);
  }

  public static long getLong(byte[] bytes, int offset) {
    long r = 0;
    for (int i = 0; i < 8; i++) {
      r |= (((long) bytes[offset + i]) & 0xFF) << i*8;
    }
    return r;
  }

  public int getInt(int offset) {
    if (offset + 4 > mySize)
      throw new IndexOutOfBoundsException(offset + " " + mySize);
    return getInt(myBytes, offset);
  }

  public static int getInt(byte[] bytes, int offset) {
    int r = 0;
    for (int i = 0; i < 4; i++)
      r |=  (((int) bytes[offset + i]) & 0xFF) << i*8;
    return r;
  }

  public byte[] getBytes(int offset, int length) {
    byte[] bytes = new byte[length];
    getBytes(offset, bytes, length);
    return bytes;
  }

  public void getBytes(int offset, byte[] bytes, int length) {
    if (offset  + length > mySize)
      throw new IndexOutOfBoundsException(offset + " " + mySize);
    System.arraycopy(myBytes, offset, bytes, 0, length);
  }

  public void getBytes(int offset, byte[] bytes, int arrayOffset, int count) {
    if (offset + count > mySize)
      throw new IndexOutOfBoundsException(offset + " " + mySize);
    System.arraycopy(myBytes, offset, bytes, arrayOffset, count);
  }

  public void setInt(int offset, int value) {
    ensureCapacity(offset + 4);
    setInt(value, myBytes, offset);
    mySize = Math.max(mySize, offset + 4);
  }

  public void setByte(int offset, byte value) {
    ensureCapacity(offset + 1);
    myBytes[offset] = value;
    mySize = Math.max(mySize, offset + 1);
  }

  public static void setInt(int value, byte[] bytes, int offset) {
    int mask = 0xFF;
    for (int i = 0; i < 4; i++) {
      bytes[offset + i] = (byte) ((value & mask) >> i * 8);
      mask = mask << 8;
    }
  }

  public void setLong(int offset, long value) {
    ensureCapacity(offset + 8);
    byte[] bytes = myBytes;
    setLong(value, bytes, offset);
    mySize = Math.max(mySize, offset + 8);
  }

  public static void setLong(long value, byte[] bytes, int offset) {
    long mask = 0xFF;
    for (int i = 0; i < 8; i++) {
      bytes[offset + i] = (byte) ((value & mask) >> i * 8);
      mask = mask << 8;
    }
  }

  public void setBit(int bitIndex, boolean set) {
    int byteIndex = bitIndex / 8;
    ensureCapacity(byteIndex + 1);
    byte bitMask = (byte) (1 << (bitIndex % 8));
    if (set)
      myBytes[byteIndex] |= bitMask;
    else
      myBytes[byteIndex] &= (bitMask ^ 0xFF);
    mySize = Math.max(mySize, byteIndex + 1);
  }

  public boolean isBitSet(int bitIndex) {
    assert checkIndex(bitIndex / 8);
    return isBitSet(myBytes, bitIndex);
  }

  public byte getByte(int index) {
    assert checkIndex(index) : index;
    return myBytes[index];
  }

  private boolean checkIndex(int index) {
    return index >= 0 && index < mySize;
  }

  public static boolean isBitSet(byte[] bytes, int bitIndex) {
    byte aByte = bytes[bitIndex / 8];
    return (aByte & (byte)(1 << (bitIndex %8))) != 0;
  }

  public void moveBytes(int from, int to, int shift) {
    if (shift == 0 || to <= from) 
      return;
    System.arraycopy(myBytes, from, myBytes, from + shift, to - from);
  }
}
