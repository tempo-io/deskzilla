package com.almworks.database.value;

import com.almworks.api.database.Artifact;
import com.almworks.api.database.Value;
import com.almworks.database.Basis;
import com.almworks.util.collections.TypedReadAccessor;
import util.external.CompactInt;

import java.io.*;

/**
 * Changes:
 * ReferenceValueType2 - change to CompactInt
 *
 * @author sereda
 */
public class ReferenceValueType2 extends AbstractValueType {
  private Basis myBasis;

  public ReferenceValueType2(Basis basis) {
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
  }

  public ValueBase createBase() {
    return new ReferenceValue(myBasis, this);
  }

  public Value read(DataInput inputStream) throws IOException {
    long v = CompactInt.readLong(inputStream);
    Artifact object = myBasis.getArtifact(v);
    return new ReferenceValue(myBasis, this, object);
  }

  public void write(DataOutput outputStream, Value value) throws IOException {
    long v = myBasis.getAtomID(((ReferenceValue) value).getArtifact());
    CompactInt.writeLong(outputStream, v);
  }
}
