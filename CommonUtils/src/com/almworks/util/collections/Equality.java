package com.almworks.util.collections;

import org.almworks.util.Util;

public interface Equality<T> {
  Equality GENERAL = new Equality() {
    public boolean areEqual(Object o, Object o1) {
      return Util.equals(o, o1);
    }
  };

  Equality IDENTITY = new Equality() {
    public boolean areEqual(Object o1, Object o2) {
      return o1 == o2;
    }
  };

  boolean areEqual(T o1, T o2);
}
