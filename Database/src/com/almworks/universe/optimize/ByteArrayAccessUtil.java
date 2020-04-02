package com.almworks.universe.optimize;

class ByteArrayAccessUtil {
  static long getLong(byte[] array, int offset) {
    long r = 0;
    r |= ((long)array[offset + 7]) & 0xFF;
    r |= (((long)array[offset + 6]) & 0xFF) << 8;
    r |= (((long)array[offset + 5]) & 0xFF) << 16;
    r |= (((long)array[offset + 4]) & 0xFF) << 24;
    r |= (((long)array[offset + 3]) & 0xFF) << 32;
    r |= (((long)array[offset + 2]) & 0xFF) << 40;
    r |= (((long)array[offset + 1]) & 0xFF) << 48;
    r |= (((long)array[offset]) & 0xFF) << 56;
    return r;
  }

  static void setInteger(byte[] array, int offset, int value) {
    array[offset++] = (byte) ((value >>> 24) & 0xFF);
    array[offset++] = (byte) ((value >>> 16) & 0xFF);
    array[offset++] = (byte) ((value >>> 8) & 0xFF);
    array[offset] = (byte) (value & 0xFF);
  }

  static void setLong(byte[] array, int offset, long value) {
    array[offset++] = (byte) ((value >>> 56) & 0xFF);
    array[offset++] = (byte) ((value >>> 48) & 0xFF);
    array[offset++] = (byte) ((value >>> 40) & 0xFF);
    array[offset++] = (byte) ((value >>> 32) & 0xFF);
    array[offset++] = (byte) ((value >>> 24) & 0xFF);
    array[offset++] = (byte) ((value >>> 16) & 0xFF);
    array[offset++] = (byte) ((value >>> 8) & 0xFF);
    array[offset] = (byte) (value & 0xFF);
  }

  public static int getInteger(byte[] array, int offset) {
    int r = 0;
    r |= ((int)array[offset + 3]) & 0xFF;
    r |= (((int)array[offset + 2]) & 0xFF) << 8;
    r |= (((int)array[offset + 1]) & 0xFF) << 16;
    r |= (((int)array[offset]) & 0xFF) << 24;
    return r;
  }
}
