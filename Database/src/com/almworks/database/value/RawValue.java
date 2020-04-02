package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.api.database.ValueType;
import org.almworks.util.Const;

import java.util.Arrays;

public class RawValue extends ValueBase {
  protected byte[] myValue = Const.EMPTY_BYTES;

  public RawValue(ValueType type) {
    super(type);
  }

  public RawValue(ValueType type, byte[] value) {
    super(type);
    setBytes(value);
  }

  protected boolean buildValue(Object rawData) {
    if (rawData == null)
      return setBytes(null);
    else if (rawData instanceof byte[])
      return setBytes((byte[]) rawData);
    else
      return false;
  }

  protected boolean copyValue(Value anotherValue) {
    if (anotherValue instanceof RawValue) {
      myValue = ((RawValue) anotherValue).getBytes();
      return true;
    }
    return false;
  }

  private boolean setBytes(byte[] value) {
    myValue = value == null ? Const.EMPTY_BYTES : value;
    return true;
  }

  public byte[] getBytes() {
    return myValue;
  }

  public int hashCode() {
    return Arrays.hashCode(myValue);
  }

  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof RawValue))
      return false;
    return Arrays.equals(myValue, ((RawValue) obj).myValue);
  }

  public String toString() {
    return "raw[" + myValue.length + "]";
  }
}
