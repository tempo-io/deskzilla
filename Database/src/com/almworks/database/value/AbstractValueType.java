package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.api.database.ValueType;
import com.almworks.util.Pair;
import com.almworks.util.collections.ConvertingIterator;
import com.almworks.util.collections.TypedReadAccessor;
import org.almworks.util.Collections15;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public abstract class AbstractValueType implements ValueType {
  protected final Class myValueClass;

  /**
   * Two index-bound lists. (Instead one list of Pair<Integer, TypedReadAccessor>)
   */
  private final List<Integer> myAccessorWeights = Collections15.arrayList();
  private final List<TypedReadAccessor> myAccessors = Collections15.arrayList();


  private static final int LOWEST_ORDER = 100;

  // cached accessor
  private volatile Pair<Class, TypedReadAccessor> myLastAccessor = null;

  private static final TypedReadAccessor<Value, Value> VALUE_ACCESSOR =
    new TypedReadAccessor<Value, Value>(Value.class) {
      public Value getValue(Value value) {
        return value;
      }
    };

  protected AbstractValueType(Class valueClass) {
    myValueClass = valueClass;
    registerAccessor(LOWEST_ORDER, VALUE_ACCESSOR);
  }

  /**
   * Adds accessor to accessor list.
   * Accessor will be matched to the requested class one by one according to specified order.
   * See {@link #getAccessor(Class<? extends T>)}
   */
  protected final <T> void registerAccessor(int order, TypedReadAccessor<Value, T> accessor) {
    int index = Collections.binarySearch(myAccessorWeights, order);
    if (index >= 0) {
      assert false : this + " " + order + " " + index;
      return;
    }
    index = -index - 1;
    assert index >= 0 && index <= myAccessorWeights.size() : index + " " + myAccessorWeights;
    assert myAccessors.size() == myAccessorWeights.size() : myAccessors + " " + myAccessorWeights;
    myAccessorWeights.add(index, order);
    myAccessors.add(index, accessor);
  }

  protected abstract ValueBase createBase();

  public Value createEmpty() {
    return createBase();
  }

  public Value create(Object rawData) {
    // :todo: in case rawData is Value, we do copying here. but maybe just pass the reference?
    ValueBase value = createBase();
    value.setValue(rawData);
    return value;
  }

  public Value tryCreate(Object rawData) {
    ValueBase value = createBase();
    boolean result = value.trySetValue(rawData);
    return result ? value : null;
  }

//  private static final DebugDichotomy __dicho = new DebugDichotomy("cached", "sought", 1000);

  public final <T> TypedReadAccessor<Value, T> getAccessor(Class<? extends T> typeClass) {
    Pair<Class, TypedReadAccessor> lastAccessor = myLastAccessor;
    if (lastAccessor != null) {
      Class lastClass = lastAccessor.getFirst();
      if (typeClass.equals(lastClass)) {
        // not assignable - to be extra sure that we don't mix accessors
        return lastAccessor.getSecond();
      }
    }
    // first try direct == with classes to avoid costly isAssignableFrom
    for (TypedReadAccessor accessor : myAccessors) {
      Class c = accessor.getTypeClass();
      if (typeClass == c) {
        myLastAccessor = Pair.create((Class) typeClass, accessor);
        return accessor;
      }
    }

    for (TypedReadAccessor accessor : myAccessors) {
      Class c = accessor.getTypeClass();
      if (typeClass.isAssignableFrom(c)) {
        myLastAccessor = Pair.create((Class) typeClass, accessor);
        return accessor;
      }
    }
    return null;
  }

  public final Iterator<Class> getAccessibleClasses() {
    return ConvertingIterator.create(Collections.unmodifiableCollection(myAccessors), TypedReadAccessor.GET_TYPE_CLASS);
  }

  public boolean equals(Object obj) {
    if (obj == null)
      return false;
    return getClass().equals(obj.getClass());
  }

  public int hashCode() {
    return getClass().hashCode();
  }

  public String toString() {
    String r = getClass().getName();
    r = r.substring(r.lastIndexOf('.') + 1);
    if (r.endsWith("ValueType"))
      r = r.substring(0, r.length() - "ValueType".length());
    return r;
  }
}
