package com.almworks.database.filter;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;
import com.almworks.api.database.util.WorkspaceUtils;
import com.almworks.database.Basis;

class AttributeContainsReference extends AttributeBasedFilter {
  private final ArtifactPointer myData;
  private volatile Value mySampleValue = null;
  private volatile String myPersistableKey = null;

  public AttributeContainsReference(final Basis basis, ArtifactPointer attribute, ArtifactPointer data,
    boolean indexable)
  {
    super(basis, attribute, indexable);
    assert data != null;
    myData = data;
  }

  public boolean accept(Revision revision) {
    if (myData == null)
      return false;
    Artifact[] value = revision.getValue(myAttribute, Artifact[].class);
    if (value == null)
      return false;
    for (Artifact a : value) {
      if (WorkspaceUtils.equals(a, myData))
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
    return "contains(" + name + ", " + myData + ")";
  }

  public int hashCode() {
    return super.hashCode() + (myData == null ? 29891 : myData.hashCode());
  }

  public boolean equals(Object o) {
    if (!(o instanceof AttributeContainsReference))
      return false;
    AttributeContainsReference that = (AttributeContainsReference) o;
    return WorkspaceUtils.equals(myAttribute, that.myAttribute) && WorkspaceUtils.equals(myData, that.myData);
  }

  public String getPersistableKey() {
    if (myPersistableKey != null)
      return myPersistableKey;
    synchronized (this) {
      if (myPersistableKey != null)
        return myPersistableKey;
      StringBuilder buffer = new StringBuilder("AttributeContainsReference:");
      buffer.append(getAttributeID());
      buffer.append(':');
      if (myData == null)
        buffer.append("null");
      else
        buffer.append(myData.getPointerKey());
      myPersistableKey = buffer.toString();
      return myPersistableKey;
    }
  }
}