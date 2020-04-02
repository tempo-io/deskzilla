package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.util.collections.TypedReadAccessor;
import util.external.CompactChar;

import java.io.*;
import java.math.BigDecimal;

/**
 * :todoc:
 *
 * @author sereda
 */
public class DecimalValueType extends AbstractValueType {
  public DecimalValueType() {
    super(DecimalValue.class);

    registerAccessor(0, new TypedReadAccessor<Value, BigDecimal>(BigDecimal.class) {
      public BigDecimal getValue(Value decimalValue) {
        if (!(decimalValue instanceof DecimalValue))
          return null;
        else
          return ((DecimalValue) decimalValue).getDecimal();
      }
    });

    registerAccessor(10, new TypedReadAccessor<Value, String>(String.class) {
      public String getValue(Value decimalValue) {
        return decimalValue.toString();
      }
    });
  }

  public ValueBase createBase() {
    return new DecimalValue(this);
  }

  public Value read(DataInput inputStream) throws IOException {
    String string = CompactChar.readString(inputStream);
    try {
      return new DecimalValue(this, new BigDecimal(string));
    } catch (NumberFormatException e) {
      throw new IOException("bad decimal " + string);
    }
  }

  public void write(DataOutput outputStream, Value value) throws IOException {
    CompactChar.writeString(outputStream, ((DecimalValue) value).getDecimal().toString());
  }
}
