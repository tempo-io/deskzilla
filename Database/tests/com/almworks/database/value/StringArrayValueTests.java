package com.almworks.database.value;

import com.almworks.api.database.Artifact;
import com.almworks.api.database.RevisionAccess;
import com.almworks.database.WorkspaceFixture;

public class StringArrayValueTests extends WorkspaceFixture {
  public void testTextArray() {
    Artifact artifact = createObjectAndSetValue(myAttributeOne, "1");
    String[] value = artifact.getLastRevision().getValue(myAttributeTwo, String[].class);
    assertNull(value);

    check(artifact);
    check(artifact, "");
    check(artifact, "xxx");
    check(artifact, "x", "y", "z");
  }

  private void check(Artifact artifact, String... values) {
    changeArtifact(artifact, RevisionAccess.ACCESS_DEFAULT, myAttributeTwo, values);
    String[] v = artifact.getLastRevision().getValue(myAttributeTwo, String[].class);
    assertEquals(v.length, values.length);
    for (int i = 0; i < v.length; i++)
      assertEquals(i + ":", v[i], values[i]);
  }
}
