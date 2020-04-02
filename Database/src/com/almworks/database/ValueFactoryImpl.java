package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.universe.Particle;
import com.almworks.database.value.PlainTextValueType;
import com.almworks.universe.optimize.PBytesHosted;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;

import java.io.*;
import java.util.*;

/**
 * @author sereda
 */
public class ValueFactoryImpl implements ValueFactory {
  private final Object myLock = new Object();
  private final Map<Long, ValueType> myTypes = Collections15.hashMap();
  private final Map<ValueType, Long> myObjects = Collections15.hashMap();
  private final SortedSet<ValueTypeInfo> myTypeOrder = Collections15.treeSet();
  private final Map<Class, ValueTypeInfo> myClassTypeInfo = Collections15.hashMap();
  private final ConsistencyWrapper myConsistencyWrapper;
  private final SystemObjectResolver mySystemObjectResolver;

  public ValueFactoryImpl(ConsistencyWrapper consistencyWrapper, SystemObjectResolver systemObjectResolver) {
    assert consistencyWrapper != null;
    assert systemObjectResolver != null;
    myConsistencyWrapper = consistencyWrapper;
    mySystemObjectResolver = systemObjectResolver;
  }

  public Value unmarshall(Particle particle) throws DatabaseInconsistentException {
//    int length = particle.getByteLength();
    if (particle instanceof Particle.PFileHostedBytes) {
      Particle.PFileHostedBytes hosted = ((Particle.PFileHostedBytes) particle);
      int typeId = hosted.getFirstLong();
      if (typeId == UNSET_TYPE_ATOMID)
        return UNSET;
      ValueType valueType = getValueType(typeId);
      if (valueType instanceof PlainTextValueType) {
        return ((PlainTextValueType) valueType).readFileHosted(hosted.getOffset(), hosted.getLength());
      } else {
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(hosted.rawHosted()));
        try {
          return valueType.read(stream);
        } catch (IOException e) {
          throw new DatabaseInconsistentException("unexpected IOException", e);
        }
      }
    } else {
      InputStream stream = new ByteArrayInputStream(particle.raw());
      return unmarshall(stream, particle);
    }
  }

  public Value unmarshall(InputStream stream, Particle particle) throws DatabaseInconsistentException {
    try {
      DataInputStream dataStream = new DataInputStream(stream);
      long typeId = dataStream.readLong(); // todo CompactInt
      if (typeId == UNSET_TYPE_ATOMID)
        return UNSET;
      ValueType valueType = getValueType(typeId);
      if ((particle instanceof PBytesHosted) && (valueType instanceof PlainTextValueType)) {
        // hack for strings
        PBytesHosted hosted = ((PBytesHosted) particle);
        // +8 - skip type reference
        int cut = 8;
        return ((PlainTextValueType) valueType).readMemoryHosted(hosted.getHostedOffset() + cut,
          hosted.getByteLength() - cut);
      } else {
        return valueType.read(dataStream);
      }
    } catch (IOException e) {
      throw new DatabaseInconsistentException("unexpected IOException", e);
    }
  }

  private ValueType getValueType(long typeAtomID) throws DatabaseInconsistentException {
    ValueType valueType;
    synchronized (myLock) {
//        Artifact type = myBasis.getArtifact(new AtomKey(typeAtomID));
      valueType = myTypes.get(typeAtomID);
      if (valueType == null)
        throw new DatabaseInconsistentException("no value type registered for artifact " + typeAtomID);
    }
    return valueType;
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
            outputStream.writeLong(UNSET_TYPE_ATOMID);
          } else {
            ValueType type = value.getType();
            synchronized (myLock) {
              long typeObject = getTypeArtifact(type);
              outputStream.writeLong(typeObject);
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

        // 1. Try to create value by looking at data class
        ValueTypeInfo primaryInfo = getTypeInfoByClass(valueData.getClass());
        if (primaryInfo != null) {
          Value value = primaryInfo.getValueType().tryCreate(valueData);
          if (value != null)
            return value;
        }

        // 2. Go through available types in priority order
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

  private ValueTypeInfo getTypeInfoByClass(Class clazz) {
    synchronized (myLock) {
      ValueTypeInfo typeInfo = myClassTypeInfo.get(clazz);
      if (typeInfo != null)
        return typeInfo;

      Iterator<Map.Entry<Class, ValueTypeInfo>> ii = myClassTypeInfo.entrySet().iterator();
      while (ii.hasNext()) {
        Map.Entry<Class, ValueTypeInfo> entry = ii.next();
        if (entry.getKey().isAssignableFrom(clazz))
          return entry.getValue();
      }
    }

    return null;
  }

  public void installType(ArtifactPointer typeObject, ValueType valueType, Class primaryDataClass,
    Class secondaryDataClass, int priority)
  {
    long key = typeObject.getPointerKey();
    synchronized (myLock) {
      myTypes.put(key, valueType);
      myObjects.put(valueType, key);
      ValueTypeInfo typeInfo = new ValueTypeInfo(valueType, primaryDataClass, priority);
      myTypeOrder.add(typeInfo);
      if (primaryDataClass != null)
        myClassTypeInfo.put(primaryDataClass, typeInfo);
      if (secondaryDataClass != null)
        myClassTypeInfo.put(secondaryDataClass, typeInfo);
    }
  }

  public ValueType getType(long typeArtifact) {
    synchronized (myLock) {
      return myTypes.get(typeArtifact);
    }
  }
}
