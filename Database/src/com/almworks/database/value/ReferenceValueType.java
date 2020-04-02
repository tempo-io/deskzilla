package com.almworks.database.value;

import com.almworks.api.database.Artifact;
import com.almworks.api.database.Value;
import com.almworks.api.database.typed.*;
import com.almworks.database.Basis;
import com.almworks.util.collections.TypedReadAccessor;

import java.io.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ReferenceValueType extends AbstractValueType {
  private Basis myBasis;

  public ReferenceValueType(Basis basis) {
    super(ReferenceValue.class);
    myBasis = basis;

    registerAccessor(0, new TypedReadAccessor<Value, Artifact>(Artifact.class) {
      public Artifact getValue(Value value) {
        if (!(value instanceof ReferenceValue))
          return null;
        else
          return ((ReferenceValue) value).getArtifact();
      }
    });

    // accessors for increasing speed

    registerAccessor(10, new TypedReadAccessor<Value, ArtifactType>(ArtifactType.class) {
      public ArtifactType getValue(Value value) {
        if (!(value instanceof ReferenceValue))
          return null;
        else {
          Artifact artifact = ((ReferenceValue) value).getArtifact();
          if (artifact == null) {
            return null;
          }
          TypedArtifact typed = myBasis.getTypedObject(artifact);
          if (typed instanceof ArtifactType) {
            return (ArtifactType) typed;
          } else {
            return null;
          }
        }
      }
    });
    registerAccessor(20, new TypedReadAccessor<Value, Attribute>(Attribute.class) {
      public Attribute getValue(Value value) {
        if (!(value instanceof ReferenceValue))
          return null;
        else {
          Artifact artifact = ((ReferenceValue) value).getArtifact();
          if (artifact == null) {
            return null;
          }
          TypedArtifact typed = myBasis.getTypedObject(artifact);
          if (typed instanceof Attribute) {
            return (Attribute) typed;
          } else {
            return null;
          }
        }
      }
    });
  }

  public ValueBase createBase() {
    return new ReferenceValue(myBasis, this);
  }

  public Value read(DataInput inputStream) throws IOException {
    long atomID = inputStream.readLong();
    Artifact object = atomID <= 0 ? null : myBasis.getArtifact(atomID);
    return new ReferenceValue(myBasis, this, object);
  }

  public void write(DataOutput outputStream, Value value) throws IOException {
    Artifact artifact = ((ReferenceValue) value).getArtifact();
    long atomID = artifact != null ? myBasis.getAtomID(artifact) : 0;
    outputStream.writeLong(atomID);
  }
}
