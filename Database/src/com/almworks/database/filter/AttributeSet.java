package com.almworks.database.filter;

import com.almworks.api.database.ArtifactPointer;
import com.almworks.api.database.Revision;
import com.almworks.api.database.typed.Attribute;
import com.almworks.database.Basis;

class AttributeSet extends AttributeBasedFilter {
  public AttributeSet(Basis basis, ArtifactPointer attribute, boolean indexable) {
    super(basis, attribute, indexable);
  }

  public boolean accept(Revision revision) {
    return revision.getValues().get(myAttribute) != null;
  }

  public String toString() {
    String name;
    try {
      Attribute attribute = myAttribute.getArtifact().getTyped(Attribute.class);
      name = attribute == null ? "<bad attribute>" : attribute.getName();
    } catch (AssertionError e) {
      // ignore (threads?)
      name = String.valueOf(myAttribute.getPointerKey());
    }
    return "isset(" + name + ")";
  }

  public String getPersistableKey() {
    return "AttributeSet:" + getAttributeID();
  }
}
