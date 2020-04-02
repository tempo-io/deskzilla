package com.almworks.util.model;

import com.almworks.util.collections.Equality;
import com.almworks.util.collections.SimpleModifiable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author dyoma
 */
public class ValueModel<T> extends SimpleModifiable {
  private final Equality<? super T> myEquality;
  private final AtomicReference<T> myValue = new AtomicReference<T>();

  public ValueModel() {
    //noinspection unchecked
    this(Equality.GENERAL, null);
  }

  public ValueModel(Equality<? super T> equality, T initial) {
    myEquality = equality;
    myValue.set(initial);
  }

  public T getValue() {
    return myValue.get();
  }

  public void setValue(T value) {
    T oldValue = myValue.getAndSet(value);
    if (!myEquality.areEqual(oldValue, value)) fireChanged();
  }

  public static <T> ValueModel<T> create() {
    return new ValueModel<T>();
  }

  public static <T> ValueModel<T> create(T initial) {
    return new ValueModel<T>(Equality.GENERAL, initial);
  }
}
