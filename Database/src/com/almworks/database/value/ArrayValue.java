package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.api.database.ValueType;
import org.almworks.util.Collections15;

import java.util.Set;

public class ArrayValue extends ValueBase {
  private static final Value[] EMPTY = {};

  protected final ValueType myScalarType;
  protected Value[] myValues = EMPTY;

  protected ArrayValue(ValueType type, ValueType scalarType) {
    super(type);
    myScalarType = scalarType;
  }

  public ArrayValue(ValueType type, ValueType scalarType, Value[] values) {
    super(type);
    myScalarType = scalarType;
    setValues(values);
  }

  // :todo: direct access to array, ArrayValueType-friendly
  Value[] getValues() {
    return myValues;
  }

  protected boolean buildValue(Object rawData) {
    if (rawData == null) {
      myValues = EMPTY;
      return true;
    } else if (rawData instanceof Object[]) {
      Object[] arr = (Object[]) rawData;
      if (arr.length == 0) {
        myValues = EMPTY;
        return true;
      }
      Value[] varr = new Value[arr.length];
      for (int i = 0; i < arr.length; i++) {
        if (arr[i] instanceof Value) {
          varr[i] = (Value) arr[i];
          if (!varr[i].getType().equals(myScalarType))
            return false;
        } else {
          varr[i] = myScalarType.tryCreate(arr[i]);
          if (varr[i] == null)
            return false;
        }
      }
      setValues(varr);
      return true;
    } else {
      return false;
    }
  }

  protected boolean copyValue(Value anotherValue) {
    if (anotherValue instanceof ArrayValue) {
      setValues(((ArrayValue) anotherValue).getValues());
      return true;
    }
    return false;
  }

  private void setValues(Value[] varr) {
    myValues = (varr == null || varr.length == 0) ? EMPTY : varr;
  }

  public int hashCode() {
    int hashCode = 41;
    for (Value value : myValues) {
      hashCode = hashCode * 41 + (value != null ? value.hashCode() : 9911);
    }
    return hashCode;
  }

  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!(obj instanceof ArrayValue))
      return false;
    ArrayValue that = (ArrayValue) obj;
    if (that.myValues.length != myValues.length)
      return false;
    for (int i = 0; i < myValues.length; i++) {
      if (!myValues[i].equals(that.myValues[i]))
        return false;
    }
    return true;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer("[[");
    for (int i = 0; i < myValues.length; i++) {
      if (i > 0)
        sb.append(",");
      sb.append(myValues[i]);
    }
    sb.append("]]");
    return sb.toString();
  }

  public long[] getReferredKeys() {
    if (myValues == null)
      return null;
    Set<Long> result = null;
    for (Value value : myValues) {
      if (!(value instanceof ValueInternals))
        continue;
      long[] refArray = ((ValueInternals) value).getReferredKeys();
      if (refArray != null && refArray.length > 0) {
        if (result == null)
          result = Collections15.hashSet();
        for (long ref : refArray)
          result.add(ref);
      }
    }
    if (result == null)
      return null;
    long[] resultArray = new long[result.size()];
    int i = 0;
    for (Long v : result) {
      resultArray[i++] = v;
    }
    return resultArray;
  }
}
