package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.integration.data.BugInfoMinimal;
import com.almworks.util.files.FileUtil;
import com.almworks.util.tests.BaseTestCase;

import java.io.IOException;
import java.util.List;

public class BadCsvLoadQueryTests extends BaseTestCase {
  private static final String RESOURCE = "com/almworks/bugzilla/integration/oper/BadCsv.csv";

  public void testRegression() throws IOException {
    // badly formed CSV
    String csv = FileUtil.loadTextResource(RESOURCE, BadCsvLoadQueryTests.class.getClassLoader());
    List<BugInfoMinimal> r = LoadQuery.tryLoadFromCsv(csv);
    assertNotNull(r);
    assertEquals(3, r.size());
    assertEquals(51, (int)r.get(0).getID());
    assertEquals(35, (int)r.get(1).getID());
    assertEquals(52, (int)r.get(2).getID());
    assertEquals(3, r.size());
    assertEquals("2009-01-22 11:27:51", r.get(0).getStringMTime());
    assertEquals("2009-01-22 11:36:07", r.get(1).getStringMTime());
    assertEquals("2009-01-22 09:12:28", r.get(2).getStringMTime());
  }
}
