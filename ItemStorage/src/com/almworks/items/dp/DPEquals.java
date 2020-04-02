package com.almworks.items.dp;

import com.almworks.items.api.*;
import com.almworks.items.util.DatabaseUtil;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.Collections15;

import java.util.Collections;
import java.util.List;

public class DPEquals<V> extends DPAttribute<V> {
  private final List<V> myValues;
  private final int myHashDPA;

  private DPEquals(DBAttribute<V> attribute, List<V> values) {
    super(attribute);
    myValues = values;
    int hash = 0;
    for (V value : values) {
      hash = hash ^ DatabaseUtil.valueHash(value);
    }
    myHashDPA = hash;
  }
  
  public static <T> BoolExpr<DP> create(DBAttribute<T> attribute, T value) {
    return new DPEquals<T>(attribute, Collections.<T>singletonList(value)).term();
  }

  public static <T extends Comparable<T>> BoolExpr<DP> equalOneOf(DBAttribute<T> attribute, Iterable<T> values) {
    List<T> list = Collections15.arrayList(values);
    Collections.sort(list);
    return new DPEquals<T>(attribute, list).term();
  }

  @Override
  protected boolean acceptValue(V value, DBReader reader) {
    for (V v : myValues) {
      if (DatabaseUtil.valueEquals(value, v)) return true;
    }
    return false;
  }

  @Override
  public String toString() {
    if (myValues.size() == 1) return getAttribute() + " == " + DatabaseUtil.valueString(myValues.get(0));
    else return getAttribute() + " in " + myValues.toString();
  }

  @Override
  protected boolean equalDPA(DPAttribute other) {
    return DatabaseUtil.isSetsEqual(myValues, ((DPEquals)other).myValues);
  }

  @Override
  protected int hashCodeDPA() {
    return myHashDPA;
  }

  public List<V> getValues() {
    return myValues;
  }
}
