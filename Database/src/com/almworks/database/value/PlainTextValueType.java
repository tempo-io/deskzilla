package com.almworks.database.value;

import com.almworks.api.database.*;
import com.almworks.util.collections.TypedReadAccessor;
import util.external.CompactChar;

import java.io.*;

public class PlainTextValueType extends AbstractValueType {
  //public static final String DEFAULT_ENCODING = "UTF-8";

  public PlainTextValueType() {
    super(PlainTextValue.class);

    registerAccessor(0, new TypedReadAccessor<Value, String>(String.class) {
      public String getValue(Value value) {
        if (!(value instanceof PlainTextValue))
          return null;
        else
          return ((PlainTextValue) value).getStringValue();
      }
    });

    registerAccessor(10, new TypedReadAccessor<Value, HostedString>(HostedString.class) {
      public HostedString getValue(Value value) {
        if (!(value instanceof PlainTextValue))
          return null;
        else
          return ((PlainTextValue) value).getHostedValue();
      }
    });
  }

  public ValueBase createBase() {
    return new PlainTextValue(this);
  }

  public Value read(DataInput inputStream) throws IOException {
    return new PlainTextValue(this, CompactChar.readString(inputStream));
  }

  public void write(DataOutput outputStream, Value value) throws IOException {
    CompactChar.writeString(outputStream, ((PlainTextValue) value).getStringValue());
  }

  public Value readMemoryHosted(int hostedOffset, int hostedLength) {
    return new PlainTextValue(this, new MemoryHostedString(hostedOffset, hostedLength));
  }

  public Value readFileHosted(int offset, int length) {
    return new PlainTextValue(this, new FileHostedString(offset, length));
  }
}
