package com.almworks.database.filter;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;
import com.almworks.api.database.util.WorkspaceUtils;
import com.almworks.database.Basis;
import org.almworks.util.Collections15;

import java.util.*;

class AttributeContainsAtLeastOneReference extends AttributeBasedFilter {
  private final Set<ArtifactPointer> myData;
  private volatile Value mySampleValue = null;
  private volatile String myPersistableKey = null;

  public AttributeContainsAtLeastOneReference(final Basis basis, ArtifactPointer attribute,
    Collection<? extends ArtifactPointer> data, boolean indexable)
  {
    super(basis, attribute, indexable);
    assert data != null;
    myData = Collections15.hashSet(data);
  }

  public boolean accept(Revision revision) {
    Artifact[] value = revision.getValue(myAttribute, Artifact[].class);
    if (value == null)
      return false;
    for (Artifact a : value) {
      if (myData.contains(a))
        return true;
    }
    return false;
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
    return "containsOne(" + name + ", " + myData + ")";
  }

  public int hashCode() {
    return super.hashCode() + myData.hashCode();
  }

  public boolean equals(Object o) {
    if (!(o instanceof AttributeContainsAtLeastOneReference))
      return false;
    AttributeContainsAtLeastOneReference that = (AttributeContainsAtLeastOneReference) o;
    return WorkspaceUtils.equals(myAttribute, that.myAttribute) && myData.equals(that.myData);
  }

  public String getPersistableKey() {
    if (myPersistableKey != null)
      return myPersistableKey;
    synchronized (this) {
      if (myPersistableKey != null)
        return myPersistableKey;
      StringBuilder buffer = new StringBuilder("AttributeContainsAtLeastOneReference:");
      buffer.append(getAttributeID());
      long[] keys = new long[myData.size()];
      int i = 0;
      for (ArtifactPointer a : myData) {
        keys[i++] = a.getPointerKey();
      }
      Arrays.sort(keys);
      for (long key : keys) {
        buffer.append(':');
        buffer.append(key);
      }
      myPersistableKey = buffer.toString();
      return myPersistableKey;
    }
  }
}