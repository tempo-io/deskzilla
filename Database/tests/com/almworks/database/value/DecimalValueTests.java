package com.almworks.database.value;

import com.almworks.api.database.Value;
import com.almworks.database.WorkspaceFixture;

import java.io.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class DecimalValueTests extends WorkspaceFixture {
  public void testDecimal() {
    DecimalValueType valueType = new DecimalValueType();
    DecimalValue value = (DecimalValue) valueType.create("0");
    assertEquals("0", value.getValue(String.class));
    value = (DecimalValue) valueType.create("1.00");
    assertEquals("1.00", value.getValue(String.class));
  }

  public void testDecimalStorage() throws IOException {
    DecimalValueType valueType = new DecimalValueType();
    Value value = valueType.create("0.00");

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(baos);
    valueType.write(out, value);
    out.close();

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    DataInputStream in = new DataInputStream(bais);
    Value value2 = valueType.read(in);

    assertEquals(value, value2);
    assertEquals(value.getValue(String.class), value2.getValue(String.class));
  }

  public void testArithmeticEquality() {
    DecimalValueType valueType = new DecimalValueType();
    Value v1 = valueType.create("239.00");
    Value v2 = valueType.create("239.0");
    assertEqualsBothWays(v1, v2);
  }
}
