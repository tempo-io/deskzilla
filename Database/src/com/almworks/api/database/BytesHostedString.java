package com.almworks.api.database;

import org.almworks.util.Log;
import org.almworks.util.Util;
import util.external.CompactChar;

import java.io.*;

public abstract class BytesHostedString extends HostedString {
  protected final int myOffset;
  protected final int myLength;

  public BytesHostedString(int offset, int length) {
    myOffset = offset;
    myLength = length;
  }

  protected String loadString() {
    String cached;
    assert myOffset >= 0 && myLength >= 0 : this;
    try {
      byte[] bytes = loadBytes();
      cached = CompactChar.readString(new DataInputStream(new ByteArrayInputStream(bytes)));
    } catch (IndexOutOfBoundsException e) {
      Log.warn(e);
      cached = "";
    } catch (IOException e) {
      Log.warn(e);
      cached = "";
    }
    return cached;
  }

  protected abstract byte[] loadBytes();

  public boolean isEmpty() {
    return myLength == 0;
  }

  public boolean equals(Object obj) {
    if (obj == this)
      return true;
    if (obj == null)
      return false;
    if (getClass() == obj.getClass()) {
      BytesHostedString that = ((BytesHostedString) obj);
      if (that.myLength != myLength)
        return false;
      if (that.myOffset == myOffset)
        return true;
      return Util.equals(that.getFullString(), getFullString());
    } else {
      return super.equals(obj);
    }
  }

  public int hashCode() {
    return myOffset * 7991 + myLength;
  }
}

