package com.almworks.database.typed;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.ValueTypeDescriptor;
import com.almworks.database.Basis;
import com.almworks.util.TODO;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ValueTypeDescriptorImpl extends NamedArtifactImpl implements ValueTypeDescriptor {
  public ValueTypeDescriptorImpl(Basis basis, ArtifactPointer delegate) {
    super(basis, delegate);
  }

  public ValueType getValueType() {
    ValueType valueType = myBasis.ourValueFactory.getType(getArtifact().getKey());
    if (valueType == null)
      throw TODO.failure();
    return valueType;
  }

  public boolean isValid(Revision revision) {
    return super.isValid(revision) && checkType(myBasis.TYPE.valueType);
  }

  public String toStringSafe() {
    return "[value type " + getName() + "]";
  }

  public static final class Factory extends AbstractFactory<ValueTypeDescriptor> {
    public Factory(Basis basis) {
      super(basis.TYPE.valueType, ValueTypeDescriptor.class);
    }

    public void initCreator(Basis basis, RevisionCreator newObject) {
      newObject.setValue(basis.ATTRIBUTE.type, basis.TYPE.valueType);
      newObject.setValue(basis.ATTRIBUTE.name, "<unnamed_value_type>");
    }

    public ValueTypeDescriptor createImpl(Basis basis, ArtifactPointer object) {
      return new ValueTypeDescriptorImpl(basis, object);
    }
  }
}
