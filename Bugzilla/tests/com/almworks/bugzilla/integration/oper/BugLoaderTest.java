package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.DefaultStateStorage;
import com.almworks.bugzilla.integration.HttpBasedFixture;
import com.almworks.bugzilla.integration.ServerInfo;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.util.xml.JDOMUtils;

import java.util.Collection;
import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public class BugLoaderTest extends HttpBasedFixture {
  //private static final String TEST_DATA_RESOURCE = "BugLoaderData.xml";

  private LoadBugsXML bugLoader;

  protected void setUp() throws Exception {
    super.setUp();
  }

  protected void tearDown() throws Exception {
    bugLoader = null;
    super.tearDown();
  }

  public void testGenericError() throws ConnectorException {
    try {
      Collection<BugInfo> bugs = loadBugs("ErrorDetectorTest.bz320.html", new Integer[]{1});
      fail("successfully loaded html");
    } catch (ConnectorException e) {
      e.printStackTrace();
      // ok
    }
  }

  public void testErrors() throws ConnectorException {
    Integer id1 = 100;
    Integer id2 = 1024;
    Collection<BugInfo> bugs = loadBugs("BugXMLLoader-Errors.bz218rc3.xml", new Integer[]{id1, id2});
    Map<Integer, BugInfo> map = BugInfo.TO_ID.assignKeys(bugs);
    assertEquals(2, map.size());

    BugInfo bug;
    bug = map.get(id1);
    assertNotNull(bug);
    assertEquals(id1, bug.getID());
    assertEquals(BugInfo.ErrorType.BUG_ACCESS_DENIED, bug.getError());
    bug = map.get(id2);
    assertNotNull(bug);
    assertEquals(id2, bug.getID());
    assertEquals(BugInfo.ErrorType.BUG_NOT_FOUND, bug.getError());
  }

  public void testPlainTextGetter() {
    assertEquals("Don't worry", JDOMUtils.replaceXmlEntities("Don&apos;t worry", true));
    assertEquals("I can't quote it.", JDOMUtils.replaceXmlEntities("I can&apos;t quote it.", true));
    assertEquals("'><&amp;<&>", JDOMUtils.replaceXmlEntities("&apos;&gt;&lt;&amp;amp;&lt;&amp;&gt;", true));
  }

  public void testRecursivelyEntityReplacement() {
    assertEquals("It's OK", OperUtils.replaceXmlEntities("It&amp;apos;s OK", true, true));
    assertEquals("Quotage: &amp;amp;", OperUtils.replaceXmlEntities("Quotage: &amp;amp;amp;", true, true));
    assertEquals("Quotage: &amp;amp;&amp;amp;amp&amp;amp;;&amp;amp;amp;&amp;amp;quot;&amp;amp;amp;&amp;amp;quot;",
      OperUtils.replaceXmlEntities(
        "Quotage: &amp;amp;amp;&amp;amp;amp;amp&amp;amp;amp;;&amp;amp;amp;amp;&amp;amp;amp;quot;&amp;amp;amp;amp;&amp;amp;amp;quot;",
        true, true));
    assertEquals("It's OK", OperUtils.replaceXmlEntities("It&apos;s OK", true, false));
  }

  private Collection<BugInfo> loadBugs(String resourceFileName, Integer[] IDs) throws ConnectorException {
    ServerInfo serverInfo =
      new ServerInfo(myMaterial, getTestUrl(resourceFileName), new DefaultStateStorage(), null, null, null, null);
    bugLoader = new LoadBugsXML(serverInfo, IDs, null, null, true, true);
    return bugLoader.loadBugs();
  }
}

