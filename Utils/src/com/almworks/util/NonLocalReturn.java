package com.almworks.util;

import org.jetbrains.annotations.*;

public class NonLocalReturn extends Exception {
  private final Object myValue;

  public NonLocalReturn(Object value) {
    myValue = value;
  }

  public NonLocalReturn() {
    this(null);
  }

  public Object getValue() {
    return myValue;
  }

  public <T> T getValue(@NotNull Class<T> valueClass) {
    if(valueClass.isInstance(myValue)) {
      return (T)myValue;
    }
    return null;
  }
}
