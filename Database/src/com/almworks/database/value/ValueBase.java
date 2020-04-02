package com.almworks.database.value;

import com.almworks.api.database.*;
import com.almworks.util.collections.TypedReadAccessor;

/**
 * :todoc:
 * :todo: synchronization!!!
 *
 * @author sereda
 */
public abstract class ValueBase implements Value, ValueInternals {
  protected final ValueType myType;

  protected ValueBase(ValueType type) {
    if (type == null)
      throw new NullPointerException("type");
    myType = type;
  }

  protected abstract boolean buildValue(Object rawData);

  protected abstract boolean copyValue(Value anotherValue);

  public <T> TypedReadAccessor<Value, T> getAccessor(Class<T> accessorType) {
    return myType.getAccessor(accessorType);
  }

  public <T> T getValue(Class<T> valueClass) {
    TypedReadAccessor<Value, T> accessor = getAccessor(valueClass);
    return accessor == null ? null : accessor.getValue(this);
  }

  protected void complainAboutRawData(Object rawData) {
    throw new BadValueDataException("argument (" + rawData + ") of class (" +
      (rawData == null ? "?" : rawData.getClass().getName()) +
      ") was not accepted in (" + getClass().getName() + ")");
  }

  public final void setValue(Object rawData) {
    boolean valueSet = trySetValue(rawData);
    if (!valueSet)
      complainAboutRawData(rawData);
  }

  public final boolean trySetValue(Object rawData) {
    boolean valueSet = false;
    if (rawData instanceof Value) {
      valueSet = copyValue((Value) rawData);
    }
    if (!valueSet)
      valueSet = buildValue(rawData);
    return valueSet;
  }

  public ValueType getType() {
    return myType;
  }

  public boolean equals(Object obj) {
    throw new UnsupportedOperationException(getClass() + " does not define equals()");
  }

  public int hashCode() {
    throw new UnsupportedOperationException(getClass() + " does not define hashCode()");
  }

  public long[] getReferredKeys() {
    return null;
  }
}
