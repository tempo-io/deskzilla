package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.api.database.ValueType;

import java.math.BigDecimal;

/**
 * :todoc:
 *
 * @author sereda
 */
public class DecimalValue extends ValueBase {
  public static final BigDecimal EMPTY = BigDecimal.valueOf(0);
  protected BigDecimal myValue = EMPTY;

  public DecimalValue(ValueType type) {
    super(type);
  }

  public DecimalValue(ValueType type, BigDecimal value) {
    super(type);
    setDecimal(value);
  }

  protected boolean buildValue(Object rawData) {
    if (rawData == null)
      return setDecimal(null);
    else if (rawData instanceof BigDecimal)
      return setDecimal((BigDecimal) rawData);
    else if (rawData instanceof String)
      try {
        return setDecimal(new BigDecimal((String) rawData));
      } catch (NumberFormatException e) {
        return false;
      }
    else
      return false;
  }

  protected boolean copyValue(Value anotherValue) {
    if (anotherValue instanceof DecimalValue) {
      myValue = ((DecimalValue) anotherValue).getDecimal();
      return true;
    }
    return false;
  }

  private boolean setDecimal(BigDecimal value) {
    myValue = value == null ? EMPTY : value;
    return true;
  }

  public BigDecimal getDecimal() {
    return myValue;
  }

  public int hashCode() {
    if (myValue == null)
      return 24957864;
    String s = myValue.toString();
    if (s.indexOf('.') >= 0) {
      int l = s.length();
      int k = l - 1;
      while (k >= 0) {
        char c = s.charAt(k);
        if (c != '0' && c != '.')
          break;
        k--;
      }
      if (k < l - 1)
        s = s.substring(0, k + 1);
    }
    return s.hashCode() ^ DecimalValue.class.hashCode();
  }

  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof DecimalValue))
      return false;
    BigDecimal otherValue = ((DecimalValue) obj).myValue;
    if (myValue == null)
      return otherValue == null;
    if (otherValue == null)
      return false;
    return myValue.compareTo(otherValue) == 0;
  }

  public String toString() {
    return myValue.toString();
  }
}
