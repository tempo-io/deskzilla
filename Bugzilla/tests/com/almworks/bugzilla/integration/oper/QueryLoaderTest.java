package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.bugzilla.integration.HttpBasedFixture;
import com.almworks.bugzilla.integration.data.BugInfoMinimal;
import org.almworks.util.Collections15;

import java.util.*;

public class QueryLoaderTest extends HttpBasedFixture {
  private LoadQuery myQueryLoader;

  protected void tearDown() throws Exception {
    myQueryLoader = null;
    super.tearDown();
  }

  public void testNoData() throws ConnectorException {
    myQueryLoader = new LoadQuery(myMaterial, getTestUrl("LoadQuery-Empty.bz218rc3.html"), null);
    Collection<BugInfoMinimal> bugs = myQueryLoader.loadBugs(null);
    assertTrue(bugs.size() == 0);
  }

  public void testSomeData() throws ConnectorException {
    myQueryLoader = new LoadQuery(myMaterial, getTestUrl("LoadQuery-Data.bz218rc3.html"), null);
    Collection<BugInfoMinimal> bugs = myQueryLoader.loadBugs(null);
    // check unique IDs
    Set ids = Collections15.hashSet();
    for (Iterator<BugInfoMinimal> iterator = bugs.iterator(); iterator.hasNext();)
      ids.add(iterator.next().getStringID());
    // some data
    assertEquals(21, bugs.size());
  }

  public void testLoadMultipleTables() throws ConnectorException {
    myQueryLoader = new LoadQuery(myMaterial, getTestUrl("LoadQuery-MultipleTables.bz218rc3.html"), null);
    Collection<BugInfoMinimal> bugs = myQueryLoader.loadBugs(null);
    // some data
    assertEquals(115, bugs.size());
  }

  public void testBrokenBugzilla() {
    try {
      myQueryLoader = new LoadQuery(myMaterial, getTestUrl("Bugzilla-Broken.bz216_10.html"), null);
      myQueryLoader.loadBugs(null);
      fail("Where my Integration Exception?");
    } catch (ConnectorException e) {
      System.err.println("Message: " + e.getMessage() + "\nDescription: " + e.getShortDescription());//It's OK
    }
  }
}
