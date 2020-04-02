package com.almworks.database.typed;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.ArtifactType;
import com.almworks.api.database.typed.Attribute;
import com.almworks.database.Basis;
import org.almworks.util.Collections15;
import org.almworks.util.Log;

import java.util.Collection;
import java.util.Set;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ArtifactTypeImpl extends NamedArtifactImpl implements ArtifactType {
  public ArtifactTypeImpl(Basis basis, ArtifactPointer delegate) {
    super(basis, delegate);
  }

  public boolean isValid(Revision revision) {
    return super.isValid(revision) && checkType(myBasis.TYPE.type);
  }

  public String toStringSafe() {
    return "[type " + getName() + "]";
  }

  public Collection<Attribute> getRequiredAttributes() {
    Artifact[] artifacts = getMandatoryValue(getArtifact().getLastRevision(), myBasis.ATTRIBUTE.requiredAttributes,
      Artifact[].class);
    Set<Attribute> result = getAttributes(artifacts);
    ArtifactType superType = getSuperType();
    if (superType != null && !superType.equals(this))
      result.addAll(superType.getRequiredAttributes());
    return result;
  }

  public Collection<Attribute> getOptionalAttributes() {
    Value value = getArtifact().getLastRevision().getValue(myBasis.ATTRIBUTE.optionalAttributes);
    if (value == null)
      return Collections15.emptyCollection();
    Artifact[] artifacts = value.getValue(Artifact[].class);
    if (artifacts == null) {
      Log.warn("optional attributes attribute is of wrong type");
      return Collections15.emptyCollection();
    }
    Set<Attribute> result = getAttributes(artifacts);
    ArtifactType superType = getSuperType();
    if (superType != null && !superType.equals(this))
      result.addAll(superType.getOptionalAttributes());
    return result;
  }

  public Collection<Attribute> getAllAttributes() {
    Set<Attribute> result = Collections15.hashSet();
    result.addAll(getRequiredAttributes());
    result.addAll(getOptionalAttributes());
    return result;
  }

  private Set<Attribute> getAttributes(Artifact[] artifacts) {
    Set<Attribute> result;
    if (artifacts != null) {
      result = Collections15.hashSet();
      if (artifacts != null)
        for (int i = 0; i < artifacts.length; i++) {
          Attribute attribute = artifacts[i].getTyped(Attribute.class);
          if (attribute != null)
            result.add(attribute);
        }
    } else {
      result = Collections15.emptySet();
    }
    return result;
  }

  public ArtifactType getSuperType() {
    return getMandatoryValue(getArtifact().getLastRevision(), myBasis.ATTRIBUTE.superType, ArtifactType.class);
  }

  public static final class Factory extends AbstractFactory<ArtifactType> {
    public Factory(Basis basis) {
      super(basis.TYPE.type, ArtifactType.class);
    }

    public ArtifactType createImpl(Basis basis, ArtifactPointer object) {
      return new ArtifactTypeImpl(basis, object);
    }

    public void initCreator(Basis basis, RevisionCreator newObject) {
      newObject.setValue(basis.ATTRIBUTE.type, basis.TYPE.type);
      newObject.setValue(basis.ATTRIBUTE.name, "<unnamed_type>");
    }
  }
}
