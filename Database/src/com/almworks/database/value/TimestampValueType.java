package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.util.collections.TypedReadAccessor;

import java.io.*;
import java.util.Date;

/**
 * :todoc:
 *
 * @author sereda
 */
public class TimestampValueType extends AbstractValueType {
  public TimestampValueType() {
    super(TimestampValue.class);

    registerAccessor(0, new TypedReadAccessor<Value, Date>(Date.class) {
      public Date getValue(Value value) {
        if (!(value instanceof TimestampValue))
          return null;
        else
          return ((TimestampValue) value).getDate();
      }
    });

    registerAccessor(10, new TypedReadAccessor<Value, String>(String.class) {
      public String getValue(Value value) {
        if (!(value instanceof TimestampValue))
          return null;
        else
          return ((TimestampValue) value).getString();
      }
    });
  }

  public ValueBase createBase() {
    return new TimestampValue(this);
  }

  public Value read(DataInput inputStream) throws IOException {
    return new TimestampValue(this, new Date(inputStream.readLong()));
  }

  public void write(DataOutput outputStream, Value value) throws IOException {
    outputStream.writeLong(((TimestampValue) value).getDate().getTime());
  }

}
