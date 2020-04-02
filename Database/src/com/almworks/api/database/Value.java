package com.almworks.api.database;

import com.almworks.util.collections.TypedReadAccessor;
import org.jetbrains.annotations.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface Value {
  // todo null object for Value and ValueType

  ValueType getType();

  /**
   * Returns accessor to most significant type that could be assigned to the specified type.
   *
   * @param accessorType class that you want to access value into.
   * @return accessor for the specified type, or null, if such conversion is not possible.
   */
  <T> TypedReadAccessor<Value, T> getAccessor(Class<T> accessorType);

  /**
   * If there's accessor available for the specified type, returns that value.
   * Return null otherwise.
   */
  @Nullable
  <T> T getValue(Class<T> valueClass);
}
