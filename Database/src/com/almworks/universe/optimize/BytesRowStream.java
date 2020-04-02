package com.almworks.universe.optimize;

import java.io.IOException;
import java.io.InputStream;

abstract class BytesRowStream extends InputStream {
  private final int myLength;
  private int myPosition = 0;

  protected BytesRowStream(int length) {
    myLength = length;
  }

  public final int read() throws IOException {
    if (myPosition >= myLength)
      return -1;
    int position = myPosition++;
    return getByte(position);
  }

  public final long skip(long n) throws IOException {
    int skip = (int) Math.max(Math.min(n, myLength - myPosition), 0);
    myPosition += skip;
    return skip;
  }

  public final int available() throws IOException {
    return Math.max(myLength - myPosition, 0);
  }

  protected abstract int getByte(int position);
}
