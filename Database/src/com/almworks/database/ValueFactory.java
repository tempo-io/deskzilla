package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.universe.Particle;
import com.almworks.util.collections.Containers;
import com.almworks.util.collections.TypedReadAccessor;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface ValueFactory {
  Value UNSET = new Value() {
    public ValueType getType() {
      throw new UnsupportedOperationException();
    }

    public <T> TypedReadAccessor<Value, T> getAccessor(Class<T> accessorType) {
      return null;
    }

    public <T> T getValue(Class<T> valueClass) {
      return null;
    }
  };
  int UNSET_TYPE_ATOMID = -1;

  Value create(Object data);

  ValueType getType(long typeArtifact);

  long getTypeArtifact(ValueType type) throws DatabaseInconsistentException;

  void installType(ArtifactPointer typeObject, ValueType valueType, Class primaryDataClass, Class secondaryDataClass,
    int priority);

  Object marshall(Value value);

//  Value unmarshall(InputStream stream) throws DatabaseInconsistentException;

  Value unmarshall(Particle particle) throws DatabaseInconsistentException;


  public static class ValueTypeInfo implements Comparable {
    private static int myAdditionalSortKeyCounter = 0;
    private final ValueType myValueType;
    private final int myAdditionalSortKey = ++myAdditionalSortKeyCounter;
    private final int myPriority;
    private final Class myPrimaryClass;

    public ValueTypeInfo(ValueType valueType, Class primaryDataClass, int priority) {
      myValueType = valueType;
      myPriority = priority;
      myPrimaryClass = primaryDataClass;
    }

    public int compareTo(Object o) {
      ValueFactoryImpl.ValueTypeInfo another = (ValueFactoryImpl.ValueTypeInfo) o;
      if (myValueType.equals(another.myValueType))
        return 0;
      if (myPriority != another.myPriority) {
        return Containers.compareInts(myPriority, another.myPriority);
      } else {
        // for equal priorities, sort according to the order of addition.
        return Containers.compareInts(myAdditionalSortKey, another.myAdditionalSortKey);
      }
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof ValueFactoryImpl.ValueTypeInfo))
        return false;
      return myValueType.equals(((ValueFactoryImpl.ValueTypeInfo) obj).myValueType);
    }

    public ValueType getValueType() {
      return myValueType;
    }

    public int hashCode() {
      return myValueType.hashCode();
    }

    public String toString() {
      return myValueType + ":" + myPriority;
    }

    public Class getPrimaryClass() {
      return myPrimaryClass;
    }
  }
}
