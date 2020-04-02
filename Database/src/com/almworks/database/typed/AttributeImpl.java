package com.almworks.database.typed;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;
import com.almworks.api.database.typed.ValueTypeDescriptor;
import com.almworks.database.Basis;
import org.almworks.util.Log;

/**
 * :todoc:
 *
 * @author sereda
 */
public class AttributeImpl extends NamedArtifactImpl implements Attribute {
  public AttributeImpl(Basis basis, ArtifactPointer delegate) {
    super(basis, delegate);
  }

  public ValueType getValueType() {
    ValueTypeDescriptor descriptor = getValueTypeDescriptor();
    ValueType valueType = myBasis.ourValueFactory.getType(descriptor.getArtifact().getKey());
    if (valueType == null) {
      Log.warn("attribute " + getName() + " value type points at incorrect object [" + descriptor + "]");
      return null;
    }
    return valueType;
  }

  public ValueTypeDescriptor getValueTypeDescriptor() {
    return getMandatoryValue(getArtifact().getLastRevision(), myBasis.ATTRIBUTE.valueType,
      ValueTypeDescriptor.class);
  }

  public boolean isValid(Revision revision) {
    return super.isValid(revision) && checkType(myBasis.TYPE.attribute) &&
      revision.getValues().get(myBasis.ATTRIBUTE.valueType) != null;
  }

  public String toStringSafe() {
    return "[attribute " + getName() + "]";
  }

  public static final class Factory extends AbstractFactory<Attribute> {
    public Factory(Basis basis) {
      super(basis.TYPE.attribute, Attribute.class);
    }

    protected void initCreator(Basis basis, RevisionCreator newObject) {
      newObject.setValue(basis.ATTRIBUTE.type, basis.TYPE.attribute);
      newObject.setValue(basis.ATTRIBUTE.valueType, basis.TYPE.noType);
      newObject.setValue(basis.ATTRIBUTE.name, "<unnamed_attribute>");
    }

    public Attribute createImpl(Basis basis, ArtifactPointer object) {
      return new AttributeImpl(basis, object);
    }

  }
}
