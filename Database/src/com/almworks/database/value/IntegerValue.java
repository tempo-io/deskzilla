package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.api.database.ValueType;
import org.almworks.util.Util;

/**
 * :todoc:
 *
 * @author sereda
 */
public class IntegerValue extends ValueBase {
  public static final Integer EMPTY = 0;
  protected Integer myValue = EMPTY;

  public IntegerValue(ValueType type) {
    super(type);
  }

  public IntegerValue(ValueType type, Integer value) {
    super(type);
    setInteger(value);
  }

  protected boolean buildValue(Object rawData) {
    if (rawData == null)
      return setInteger(null);
    else if (rawData instanceof Integer)
      return setInteger((Integer) rawData);
    else if (rawData instanceof Number)
      return setInteger(((Number) rawData).intValue());
    else if (rawData instanceof String)
      try {
        return setInteger(Integer.valueOf((String) rawData));
      } catch (NumberFormatException e) {
        return false;
      }
    else
      return false;
  }

  protected boolean copyValue(Value anotherValue) {
    if (anotherValue instanceof IntegerValue) {
      myValue = ((IntegerValue) anotherValue).getInteger();
      return true;
    }
    return false;
  }

  private boolean setInteger(Integer value) {
    myValue = value == null ? EMPTY : value;
    return true;
  }

  public Integer getInteger() {
    return myValue;
  }

  public int hashCode() {
    return myValue.hashCode() ^ IntegerValue.class.hashCode();
  }

  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof IntegerValue))
      return false;
    return Util.equals(myValue, ((IntegerValue) obj).myValue);
  }

  public String toString() {
    return myValue.toString();
  }
}
