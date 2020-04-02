package com.almworks.api.database;

import com.almworks.util.collections.TypedReadAccessor;

import java.io.*;
import java.util.Iterator;

/**
 * This class represents a type of Values and serves as a factory for Values.
 * :todo: vectors?
 *
 * @author sereda
 * @see Value
 */
public interface ValueType {
  /**
   * Empty values may be used as "default" values.
   */
  Value createEmpty();

  Value create(Object rawData);

  Value tryCreate(Object rawData);

  /**
   * Returns accessor that converts value to a given type, or null.
   */
  <T> TypedReadAccessor<Value, T> getAccessor(Class<? extends T> accessorClass);

  Iterator<Class> getAccessibleClasses();

  Value read(DataInput inputStream) throws IOException;

  void write(DataOutput outputStream, Value value) throws IOException;
}
