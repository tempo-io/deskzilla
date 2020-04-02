package com.almworks.database.typed;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.*;
import com.almworks.database.Basis;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class NamedArtifactImpl extends TypedArtifactImpl implements NamedArtifact {
  public NamedArtifactImpl(Basis basis, ArtifactPointer delegate) {
    super(basis, delegate);
  }

  @NotNull
  public String getName() {
    // important: do not use Revision.getValue(AttributeKey<T>), or you will cycle
    return getMandatoryValue(getArtifact().getLastRevision(), myBasis.ATTRIBUTE.name, String.class);
  }

  public String getDisplayableName() {
    Value value = getArtifact().getLastRevision().getValue(myBasis.ATTRIBUTE.displayableName);
    if (value == null)
      return getName();
    String name = value.getValue(String.class);
    if (name == null) {
      Log.warn("named object " + getArtifact() + " displayable name is set but not a character string [" + value + "]");
      return null;
    }
    return name;
  }

  public boolean isValid(Revision revision) {
    return super.isValid(revision) && getName() != null;
  }

  public Attribute attributeName() {
    return myBasis.getSystemObject(SystemObjects.ATTRIBUTE.NAME);
  }

  public String toStringSafe() {
    return "[named " + getName() + "]";
  }

  public static abstract class AbstractFactory<T extends TypedArtifact> implements TypedObjectFactory<T> {
    protected final ArtifactPointer myTypeArtifact;
    protected final Class<T> myTypedClass;

    protected AbstractFactory(ArtifactPointer typeArtifact, Class<T> typedClass) {
      assert typeArtifact != null;
      assert typedClass != null;
      myTypeArtifact = typeArtifact;
      myTypedClass = typedClass;
    }

    public final T loadTyped(Basis basis, Artifact object) {
      T impl = createImpl(basis, object);
      return impl.isValid(object.getLastRevision()) ? impl : null;
    }

    public final T initializeTyped(Basis basis, RevisionCreator newObject) {
      initCreator(basis, newObject);
      T impl = createImpl(basis, newObject);
      assert impl.isValid(newObject.asRevision());
      return impl;
    }

    protected abstract T createImpl(Basis basis, ArtifactPointer object);

    protected abstract void initCreator(Basis basis, RevisionCreator newObject);

    public ArtifactPointer getTypeArtifact(Basis basis) {
      return myTypeArtifact;
    }

    public Class<T> getTypedClass() {
      return myTypedClass;
    }
  }
}
