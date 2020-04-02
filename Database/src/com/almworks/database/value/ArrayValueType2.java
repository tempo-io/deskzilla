package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.api.database.ValueType;
import com.almworks.util.collections.TypedReadAccessor;
import util.external.CompactInt;

import java.io.*;
import java.lang.reflect.Array;
import java.util.Iterator;

/**
 * Changes in ArrayValueType:
 * ArrayValueType2 - using CompactInt
 *
 * @author sereda
 */
public class ArrayValueType2 extends AbstractValueType {
  private static final int MAX_LENGTH = 100000;
  protected final ValueType myScalarType;

  public ArrayValueType2(ValueType scalarType) {
    super(ArrayValue.class);
    myScalarType = scalarType;
/*
    registerAccessor(0, new TypedReadAccessor<ArrayValue, Value[]>(Value[].class) {
      public Value[] getValue(ArrayValue value) {
        return value.getValues();
      }
    });
*/

    int i = 0;
    for (Iterator<Class> it = scalarType.getAccessibleClasses(); it.hasNext();) {
      final Class elementClass = it.next();
      final Class arrayClass = Array.newInstance(elementClass, 0).getClass();
      registerAccessor(10 + i++, new TypedReadAccessor(arrayClass) {
        public Object getValue(Object o) {
          if (!(o instanceof ArrayValue))
            return null;
          ArrayValue arrayValue = (ArrayValue) o;
          Value[] values = arrayValue.getValues();
          Object[] result = (Object[]) Array.newInstance(elementClass, values.length);
          for (int i = 0; i < values.length; i++) {
            result[i] = values[i].getValue(elementClass);
          }
          return result;
        }
      });
    }
  }

  public ValueBase createBase() {
    return new ArrayValue(this, myScalarType);
  }

  public Value read(DataInput inputStream) throws IOException {
    int length = CompactInt.readInt(inputStream);
    if (length < 0 || length > MAX_LENGTH)
      throw new IOException("bad data (length = " + length + ")");
    Value[] values = new Value[length];
    for (int i = 0; i < values.length; i++)
      values[i] = myScalarType.read(inputStream);
    return new ArrayValue(this, myScalarType, values);
  }

  public void write(DataOutput outputStream, Value value) throws IOException {
    Value[] values = ((ArrayValue) value).getValues();
    CompactInt.writeInt(outputStream, values.length);
    for (int i = 0; i < values.length; i++)
      myScalarType.write(outputStream, values[i]);
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof ArrayValueType2))
      return false;
    return getClass().equals(obj.getClass()) && myScalarType.equals(((ArrayValueType2) obj).myScalarType);
  }

  public int hashCode() {
    return getClass().hashCode() * 23 + myScalarType.hashCode();
  }

  public String toString() {
    return super.toString() + "[" + myScalarType.toString() + "]";
  }
}
