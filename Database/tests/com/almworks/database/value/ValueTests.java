package com.almworks.database.value;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;
import com.almworks.database.WorkspaceFixture;

import java.io.*;
import java.util.Arrays;

public class ValueTests extends WorkspaceFixture {
  
  public void testReferenceValues() {
    ReferenceValueType type = new ReferenceValueType(myBasis);
    Value value = type.create(myAttributeTwo);
    Artifact object = value.getValue(Artifact.class);
    assertTrue(object.equals(myAttributeTwo.getArtifact()));
  }

  public void testReferenceValuesMore() {
    ReferenceValueType type = new ReferenceValueType(myBasis);
    Value value1 = type.create(myAttributeTwo);
    Value value2 = type.create(myAttributeOne);
    Value value3 = type.create(myAttributeTwo);
    Value value4 = type.create(null);
    assertEquals(value1, value3);
    assertEquals(value1, value1);
    assertEquals(value4, value4);
    assertTrue(!value1.equals(value2));
    assertTrue(!value1.equals(value4));
  }

  public void testReferenceValuesAcceptTypedKeys() {
    ReferenceValueType type = new ReferenceValueType(myBasis);
    Value value = type.create(SystemObjects.ATTRIBUTE.NAME);
    Artifact artifact = value.getValue(Artifact.class);
    assertEquals(artifact, myBasis.ATTRIBUTE.name.getArtifact());
    ArtifactPointer artifactPointer = value.getValue(ArtifactPointer.class);
    assertEquals(artifactPointer, artifact);

    Attribute attribute = value.getValue(Attribute.class);
    assertEquals(attribute, artifactPointer);
    assertEquals(attribute, artifact);
  }

  public void testArrayAccessors() {
    ValueType valueType = new ArrayValueType(new IntegerValueType());
    Class[] accessibleClasses = {Value.class, Value[].class, String[].class, Integer[].class};
    for (int i = 0; i < accessibleClasses.length; i++) {
      Class accessibleClass = accessibleClasses[i];
      assertTrue("for " + accessibleClass, valueType.getAccessor(accessibleClass) != null);
    }
  }

  public void testArrayEquality() {
    ValueType valueType = new ArrayValueType(new IntegerValueType());
    Value value1 = valueType.create(new Integer[] {new Integer(1), new Integer(2), new Integer(3)});
    Value value2 = valueType.create(new Integer[] {new Integer(1), new Integer(2), new Integer(3)});
    Value value3 = valueType.create(new Integer[] {new Integer(1), new Integer(3), new Integer(3)});
    Value value4 = valueType.create(new Integer[] {new Integer(1), new Integer(3), new Integer(2)});
    assertEquals(value1, value2);
    assertEquals(value2, value1);
    assertNotSame(value1, value3);
    assertNotSame(value1, value4);
    assertNotSame(value2, value3);
    assertNotSame(value2, value4);
  }

  public void testEmptyReferenceValueMarshalling() throws IOException {
    ReferenceValueType type = new ReferenceValueType(myBasis);
    Value empty = type.createEmpty();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    type.write(new DataOutputStream(out), empty);
    Value value = type.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
    assertEquals(type, value.getType());
  }

  public void testRaw() throws IOException {
    RawValueType type = new RawValueType();
    Value empty = type.createEmpty();
    assertEquals(0, ((RawValue) empty).getBytes().length);
    byte[] bytes = new byte[] {10, 9, 8, 20};
    Value value = type.create(bytes);
    assertTrue(Arrays.equals(bytes, value.getValue(byte[].class)));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    type.write(new DataOutputStream(out), value);
    Value read = type.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
    assertTrue(Arrays.equals(bytes, read.getValue(byte[].class)));
  }
}
