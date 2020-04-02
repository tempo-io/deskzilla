package com.almworks.database.value;

import com.almworks.api.database.*;
import org.almworks.util.Util;

public class PlainTextValue extends ValueBase {
  private static final String EMPTY = "";

  protected String myValue = EMPTY;
  protected HostedString myHosted = null;

  PlainTextValue(ValueType type) {
    super(type);
  }

  PlainTextValue(ValueType type, String value) {
    super(type);
    setString(value);
  }

  PlainTextValue(ValueType type, HostedString hosted) {
    super(type);
    myHosted = hosted;
  }

  public synchronized String getStringValue() {
    return myHosted == null ? myValue : myHosted.getFullString();
  }

  public synchronized HostedString getHostedValue() {
    return myHosted != null ? myHosted : new NotReallyHostedString(myValue);
  }

  protected synchronized boolean buildValue(Object rawData) {
    if (rawData == null) {
      return setString(null);
    } else if (rawData instanceof String) {
      return setString((String) rawData);
    } else if (rawData instanceof NotReallyHostedString) {
      return setString(((NotReallyHostedString) rawData).getFullString());
    } else if (rawData instanceof HostedString) {
      return setHostedString((HostedString) rawData);
    } else {
      // :todo: shall we accept other objects using toString(), or will it lead to errors?
      return false;
    }
  }

  protected synchronized boolean copyValue(Value anotherValue) {
    if (anotherValue instanceof PlainTextValue) {
      myHosted = ((PlainTextValue) anotherValue).myHosted;
      myValue = ((PlainTextValue) anotherValue).myValue;
      return true;
    } else {
      return false;
    }
  }

  private synchronized boolean setString(String value) {
    myHosted = null;
    myValue = value == null ? EMPTY : value;
    return true;
  }

  private synchronized boolean setHostedString(HostedString value) {
    myHosted = value;
    myValue = EMPTY;
    return true;
  }

  public int hashCode() {
    return getStringValue().hashCode() ^ PlainTextValue.class.hashCode();
  }

  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof PlainTextValue))
      return false;
    PlainTextValue that = (PlainTextValue) obj;
    HostedString thatHosted = that.myHosted;
    HostedString thisHosted = myHosted;
    if (thisHosted != null && thatHosted != null)
      return thisHosted.equals(thatHosted);
    else
      return Util.equals(getStringValue(), that.getStringValue());
  }

  public String toString() {
    return myHosted == null ? myValue : myHosted.toString();
  }
}
