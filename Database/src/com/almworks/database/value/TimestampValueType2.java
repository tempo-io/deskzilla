package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.util.collections.TypedReadAccessor;
import util.external.CompactInt;

import java.io.*;
import java.util.Date;

/**
 * Change:
 * TimestampValueType2 - Usage of CompactInt
 *
 * @author sereda
 */
public class TimestampValueType2 extends AbstractValueType {
  public TimestampValueType2() {
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
    long v = CompactInt.readLong(inputStream);
    return new TimestampValue(this, new Date(v));
  }

  public void write(DataOutput outputStream, Value value) throws IOException {
    long v = ((TimestampValue) value).getDate().getTime();
    CompactInt.writeLong(outputStream, v);
  }
}
