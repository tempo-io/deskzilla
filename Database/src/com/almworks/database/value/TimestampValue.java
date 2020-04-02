package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.api.database.ValueType;
import org.almworks.util.Util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * :todoc:
 *
 * @author sereda
 */
public class TimestampValue extends ValueBase {
  public static final SimpleDateFormat DEFAULT_DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");  // todo thread-safe
  public static final Date EMPTY = new Date(0);
  protected Date myValue = EMPTY;

  public TimestampValue(ValueType type) {
    super(type);
  }

  public TimestampValue(ValueType type, Date value) {
    super(type);
    setDate(value);
  }

  protected boolean buildValue(Object rawData) {
    if (rawData == null)
      return setDate(null);
    else if (rawData instanceof Date)
      return setDate((Date) rawData);
    else if (rawData instanceof Long)
      return setDate(new Date(((Long) rawData).longValue()));
    else if (rawData instanceof String)
      try {
        return setDate(DEFAULT_DATEFORMAT.parse((String) rawData));
      } catch (ParseException e) {
        return false;
      } catch (NumberFormatException e) {
        return false;
      }
    else
      return false;
  }

  protected boolean copyValue(Value anotherValue) {
    if (anotherValue instanceof TimestampValue) {
      myValue = ((TimestampValue) anotherValue).getDate();
      return true;
    }
    return false;
  }

  private boolean setDate(Date value) {
    myValue = value == null ? EMPTY : value;
    return true;
  }

  public Date getDate() {
    return myValue;
  }

  public String getString() {
    return DEFAULT_DATEFORMAT.format(myValue);
  }

  public int hashCode() {
    return myValue.hashCode() ^ TimestampValue.class.hashCode();
  }

  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof TimestampValue))
      return false;
    return Util.equals(myValue, ((TimestampValue) obj).myValue);
  }

  public String toString() {
    return getString();
  }
}
