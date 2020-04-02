package com.almworks.database.filter;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;
import com.almworks.api.database.typed.TypedArtifact;
import com.almworks.api.database.util.WorkspaceUtils;
import com.almworks.database.Basis;
import org.almworks.util.*;

import java.io.*;

class AttributeEquals extends AttributeBasedFilter implements Filter.Equals {
  private final Object myData;
  private volatile Value mySampleValue = null;
  private volatile String myPersistableKey = null;

  public AttributeEquals(final Basis basis, ArtifactPointer attribute, final Object data, boolean indexable) {
    super(basis, attribute, indexable);
    assert data != null;
    myData = data;
  }

  public ArtifactPointer getAttribute() {
    return myAttribute;
  }

  public Value getValue() {
    return getSampleValue();
  }

  public boolean accept(Revision revision) {
    Value value = revision.getValue(myAttribute);
    if (value == null)
      return false;
    Value sampleValue = getSampleValue();
    if (sampleValue == null)
      return false;
    return value.equals(sampleValue);
  }

  public String toString() {
    String name;
    try {
      Attribute attribute = myAttribute.getArtifact().getTyped(Attribute.class);
      name = attribute == null ? "<bad attribute>" : attribute.getName();
    } catch (AssertionError e) {
      // threads?
      name = String.valueOf(myAttribute.getPointerKey());
    }
    return "equals(" + name + ", '" + myData + "')";
  }

  public int hashCode() {
    return super.hashCode() + (myData == null ? 29891 : myData.hashCode());
  }

  public boolean equals(Object o) {
    if (!(o instanceof AttributeEquals))
      return false;
    AttributeEquals that = (AttributeEquals) o;
    return WorkspaceUtils.equals(myAttribute, that.myAttribute)
      && Util.equals(myData, that.myData);
  }

  public String getPersistableKey() {
    if (myPersistableKey != null)
      return myPersistableKey;
    synchronized (this) {
      if (myPersistableKey != null)
        return myPersistableKey;
      try {
        Value value = getSampleValue();
        if (value == null)
          return null;
        StringBuffer buffer = new StringBuffer("AttributeEquals:");
        buffer.append(getAttributeID());
        buffer.append(':');
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        value.getType().write(out, value);
        out.close();
        byte[] bytes = baos.toByteArray();
        for (int i = 0; i < bytes.length; i++) {
          String v = Util.upper(Integer.toHexString(((int)bytes[i]) & 0xFF));
          if (v.length() == 1)
            buffer.append('0');
          buffer.append(v);
        }
        myPersistableKey = buffer.toString();
      } catch (IOException e) {
        // weird
        throw new Failure(e);
      }
      return myPersistableKey;
    }
  }

  private Value getSampleValue() {
    if (mySampleValue != null)
      return mySampleValue;
    synchronized(this) {
      if (mySampleValue != null)
        return mySampleValue;

      TypedArtifact typedObject = myBasis.getTypedObject(myAttribute.getArtifact());
      if (!(typedObject instanceof Attribute)) {
        assert false : typedObject;
        return null;
      }

      ValueType valueType = ((Attribute) typedObject).getValueType();
      Value value = valueType.tryCreate(myData);
      if (value == null) {
        Log.warn("cannot derive " + valueType + " from " + myData);
        value = valueType.createEmpty();
      }
      mySampleValue = value;
      return mySampleValue;
    }
  }
}
