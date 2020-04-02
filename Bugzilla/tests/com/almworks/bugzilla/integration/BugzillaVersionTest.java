package com.almworks.bugzilla.integration;

import junit.framework.TestCase;

public class BugzillaVersionTest extends TestCase {
  public void testParse() throws Exception {
    String[] versionStrings = {"4.2+", "4.0.5+", "2.18.6", "4.0", "3.0.2"};
    int[][] versions = {
      {4,2,0},
      {4,0,5},
      {2,18,6},
      {4,0,0},
      {3,0,2}
    };
    for (int i = 0; i < versionStrings.length; ++i) {
      assertEquals(new BugzillaVersion(versions[i][0], versions[i][1], versions[i][2]),
        BugzillaVersion.parse(versionStrings[i]));
    }
  }
}
