package com.almworks.database.value;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.TypedArtifact;
import com.almworks.database.Basis;
import org.almworks.util.TypedKey;
import org.almworks.util.Util;


/**
 * :todoc:
 *
 * @author sereda
 */
public class ReferenceValue extends ValueBase {
  private final Basis myBasis;
  private Artifact myReferenent;

  ReferenceValue(Basis basis, ValueType type) {
    super(type);
    myBasis = basis;
  }

  ReferenceValue(Basis basis, ValueType type, Artifact referenent) {
    super(type);
    myBasis = basis;
    buildValue(referenent);
  }

  protected boolean buildValue(Object rawData) {
    if (rawData == null) {
      myReferenent = null;
    } else if (rawData instanceof Artifact) {
      myReferenent = (Artifact) rawData;
    } else if (rawData instanceof ArtifactPointer) {
      myReferenent = ((ArtifactPointer) rawData).getArtifact();
    } else if (rawData instanceof TypedKey) {
      ArtifactPointer pointer = myBasis.getSystemObject((TypedKey) rawData);
      if (pointer == null)
        return false;
      myReferenent = pointer.getArtifact();
    } else {
      return false;
    }
    return true;
  }

  protected boolean copyValue(Value anotherValue) {
    if (anotherValue instanceof ReferenceValue) {
      myReferenent = ((ReferenceValue) anotherValue).myReferenent;
      return true;
    } else {
      return false;
    }
  }

  public Artifact getArtifact() {
    return myReferenent;
  }

  public String toString() {
    return "ref{" + myReferenent + "}";
  }

  public <T> T getValue(Class<T> valueClass) {
    if (myReferenent == null)
      return null;
    T result = super.getValue(valueClass);
    if (result != null)
      return result;
    if (TypedArtifact.class.isAssignableFrom(valueClass)) {
      TypedArtifact typed = myReferenent.getTyped((Class) valueClass);
      if (typed != null)
        return (T) typed;
    }
    return null;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof ReferenceValue))
      return false;
    return Util.equals(myReferenent, ((ReferenceValue) obj).myReferenent);
  }

  public int hashCode() {
    Artifact artifact = myReferenent;
    return artifact == null ? 0 : artifact.hashCode();
  }

  public long[] getReferredKeys() {
    if (myReferenent == null)
      return null;
    else
      return new long[] {myReferenent.getKey()};
  }
}
