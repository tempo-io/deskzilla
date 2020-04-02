package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.universe.Particle;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import util.external.CompactInt;

import java.io.*;
import java.util.Map;
import java.util.SortedSet;

/**
 * Changes:
 * move to CompactInt
 *
 * @author sereda
 */
public class ValueFactoryImpl2 implements ValueFactory {
  private final Object myLock = new Object();
  private Map<Long, ValueType> myTypes = Collections15.hashMap();
  private Map<ValueType, Long> myObjects = Collections15.hashMap();
  private SortedSet<ValueTypeInfo> myTypeOrder = Collections15.treeSet();
  private final ConsistencyWrapper myConsistencyWrapper;
  private final SystemObjectResolver mySystemObjectResolver;

  public ValueFactoryImpl2(ConsistencyWrapper consistencyWrapper, SystemObjectResolver systemObjectResolver) {
    assert consistencyWrapper != null;
    assert systemObjectResolver != null;
    myConsistencyWrapper = consistencyWrapper;
    mySystemObjectResolver = systemObjectResolver;
  }

  public Value unmarshall(Particle particle) throws DatabaseInconsistentException {
    return unmarshall(particle.getStream());
  }

  public Value unmarshall(InputStream stream) throws DatabaseInconsistentException {
    try {
      ValueType valueType;
      DataInputStream dataStream;
      synchronized (myLock) {
        dataStream = new DataInputStream(stream);
        long typeAtomID = CompactInt.readLong(dataStream);
        if (typeAtomID == UNSET_TYPE_ATOMID)
          return UNSET;
        //Artifact type = myBasis.getArtifact(new AtomKey(typeAtomID));
        valueType = myTypes.get(typeAtomID);
        if (valueType == null)
          throw new DatabaseInconsistentException("no value type registered for artifact " + typeAtomID);
      }
      return valueType.read(dataStream);
    } catch (IOException e) {
      throw new DatabaseInconsistentException("unexpected IOException", e);
    }
  }

  /**
   * Creates an object that is understood by Particle factory.
   */
  public Object marshall(final Value value) {
    while (true) {
      try {
        try {
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          DataOutputStream outputStream = new DataOutputStream(baos);
          if (value == UNSET) {
            CompactInt.writeLong(outputStream, UNSET_TYPE_ATOMID);
          } else {
            ValueType type = value.getType();
            synchronized (myLock) {
              long typeObject = getTypeArtifact(type);
              CompactInt.writeLong(outputStream, typeObject);
            }
            type.write(outputStream, value);
          }
          outputStream.close();
          return baos.toByteArray();
        } catch (IOException e) {
          throw new DatabaseInconsistentException("unexpected IOException", e);
        }
      } catch (DatabaseInconsistentException e) {
        myConsistencyWrapper.handle(e, -1);
      }
    }
  }

  public long getTypeArtifact(ValueType type) throws DatabaseInconsistentException {
    Long typeObject = myObjects.get(type);
    if (typeObject == null)
      throw new DatabaseInconsistentException("no registered type artifact for valuetype " + type);
    return typeObject;
  }

  public Value create(final Object data) {
    while (true) {
      try {
        Object valueData = data;
        if (valueData == null)
          throw new NullPointerException("valueData");
        if (valueData instanceof Value)
          return (Value) valueData;
        if (valueData instanceof TypedKey) {
          // check for system objects
          Object systemObjectData = mySystemObjectResolver.getSystemObject((TypedKey) valueData);
          if (systemObjectData != null)
            valueData = systemObjectData;
        }

        ValueTypeInfo[] typeInfos;
        synchronized (myLock) {
          typeInfos = myTypeOrder.toArray(new ValueTypeInfo[myTypeOrder.size()]);
        }
        for (ValueTypeInfo typeInfo : typeInfos) {
          ValueType valueType = typeInfo.getValueType();
          Value value = valueType.tryCreate(valueData);
          if (value != null)
            return value;
        }

        throw new DatabaseInconsistentException("cannot create value from " + valueData);
      } catch (DatabaseInconsistentException e) {
        myConsistencyWrapper.handle(e, -1);
      }
    }
  }

  public void installType(ArtifactPointer typeObject, ValueType valueType, Class primaryDataClass,
    Class secondaryDataClass, int priority) {
    long key = typeObject.getPointerKey();
    synchronized (myLock) {
      myTypes.put(key, valueType);
      myObjects.put(valueType, key);
      myTypeOrder.add(new ValueTypeInfo(valueType, primaryDataClass, priority));
    }
  }

  public ValueType getType(long typeArtifact) {
    synchronized (myLock) {
      return myTypes.get(typeArtifact);
    }
  }
}