package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.util.collections.TypedReadAccessor;

import java.io.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class BooleanValueType extends AbstractValueType {
  public BooleanValueType() {
    super(BooleanValue.class);

    registerAccessor(0, new TypedReadAccessor<Value, Boolean>(Boolean.class) {
      public Boolean getValue(Value booleanValue) {
        if (!(booleanValue instanceof BooleanValue))
          return null;
        else
          return ((BooleanValue) booleanValue).getBoolean();
      }
    });

    registerAccessor(10, new TypedReadAccessor<Value, String>(String.class) {
      public String getValue(Value booleanValue) {
        if (!(booleanValue instanceof BooleanValue))
          return null;
        else
          return booleanValue.toString();
      }
    });
  }

  public ValueBase createBase() {
    return new BooleanValue(this);
  }

  public Value read(DataInput inputStream) throws IOException {
    return new BooleanValue(this, inputStream.readBoolean());
  }

  public void write(DataOutput outputStream, Value value) throws IOException {
    outputStream.writeBoolean(((BooleanValue) value).getBoolean().booleanValue());
  }

}
