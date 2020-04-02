package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.api.database.ValueType;
import org.almworks.util.Util;

/**
 * :todoc:
 *
 * @author sereda
 */
public class BooleanValue extends ValueBase {
  public static final Boolean EMPTY = Boolean.FALSE;
  protected Boolean myValue = EMPTY;

  public BooleanValue(ValueType type) {
    super(type);
  }

  public BooleanValue(ValueType type, Boolean value) {
    super(type);
    setBoolean(value);
  }

  protected boolean buildValue(Object rawData) {
    if (rawData == null)
      return setBoolean(null);
    else if (rawData instanceof Boolean)
      return setBoolean((Boolean) rawData);
//    else if (rawData instanceof String)
//      return setBoolean(Boolean.valueOf((String) rawData));
    else
      return false;
  }

  protected boolean copyValue(Value anotherValue) {
    if (anotherValue instanceof BooleanValue) {
      myValue = ((BooleanValue) anotherValue).getBoolean();
      return true;
    }
    return false;
  }

  private boolean setBoolean(Boolean value) {
    myValue = value == null ? EMPTY : value;
    return true;
  }

  public Boolean getBoolean() {
    return myValue;
  }

  public int hashCode() {
    return myValue.hashCode() ^ BooleanValue.class.hashCode();
  }

  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof BooleanValue))
      return false;
    return Util.equals(myValue, ((BooleanValue) obj).myValue);
  }

  public String toString() {
    return myValue.toString();
  }
}
