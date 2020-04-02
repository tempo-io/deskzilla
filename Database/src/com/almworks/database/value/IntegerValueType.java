package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.util.collections.TypedReadAccessor;

import java.io.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class IntegerValueType extends AbstractValueType {
  public IntegerValueType() {
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
    return new IntegerValue(this, inputStream.readInt());
  }

  public void write(DataOutput outputStream, Value value) throws IOException {
    outputStream.writeInt(((IntegerValue) value).getInteger().intValue());
  }
}
