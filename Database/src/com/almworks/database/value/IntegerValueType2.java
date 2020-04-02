package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.util.collections.TypedReadAccessor;
import util.external.CompactInt;

import java.io.*;

/**
 * Changes:
 * IntegerValueType2 - switched to CompactInt
 *
 * @author sereda
 */
public class IntegerValueType2 extends AbstractValueType {
  public IntegerValueType2() {
    super(IntegerValue.class);

    registerAccessor(0, new TypedReadAccessor<Value, Integer>(Integer.class) {
      public Integer getValue(Value integerValue) {
        if (!(integerValue instanceof IntegerValue))
          return null;
        else
          return ((IntegerValue) integerValue).getInteger();
      }
    });

    registerAccessor(10, new TypedReadAccessor<Value, String>(String.class) {
      public String getValue(Value integerValue) {
        return integerValue.toString();
      }
    });
  }

  public ValueBase createBase() {
    return new IntegerValue(this);
  }

  public Value read(DataInput inputStream) throws IOException {
    int v = CompactInt.readInt(inputStream);
    return new IntegerValue(this, v);
  }

  public void write(DataOutput outputStream, Value value) throws IOException {
    int v = ((IntegerValue) value).getInteger().intValue();
    CompactInt.writeInt(outputStream, v);
  }
}
