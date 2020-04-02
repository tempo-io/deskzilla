package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.util.collections.TypedReadAccessor;
import org.almworks.util.Const;
import util.external.CompactInt;

import java.io.*;

public class RawValueType extends AbstractValueType {
  public RawValueType() {
    super(RawValue.class);

    registerAccessor(0, new TypedReadAccessor<Value, byte[]>(byte[].class) {
      public byte[] getValue(Value value) {
        if (!(value instanceof RawValue))
          return null;
        else
          return ((RawValue) value).getBytes();
      }
    });
  }

  public ValueBase createBase() {
    return new RawValue(this);
  }

  public Value read(DataInput inputStream) throws IOException {
    int count = CompactInt.readInt(inputStream);
    byte[] bytes;
    if (count <= 0) {
      bytes = Const.EMPTY_BYTES;
    } else {
      bytes = new byte[count];
      inputStream.readFully(bytes);
    }
    return new RawValue(this, bytes);
  }

  public void write(DataOutput outputStream, Value value) throws IOException {
    byte[] bytes = ((RawValue) value).getBytes();
    if (bytes == null || bytes.length == 0) {
      CompactInt.writeInt(outputStream, 0);
    } else {
      CompactInt.writeInt(outputStream, bytes.length);
      outputStream.write(bytes);
    }
  }
}
