package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.HttpBasedFixture;
import com.almworks.bugzilla.integration.data.BugzillaLists;
import com.almworks.util.tests.CollectionsCompare;

public class AuxiliaryInfoRetrieverTest extends HttpBasedFixture {
  private LoadDictionaries myRetriever;

  protected void tearDown() throws Exception {
    myRetriever = null;
    super.tearDown();
  }

  public void testBugzilla214() throws ConnectorException {
    myRetriever = new LoadDictionaries(myMaterial, getTestUrl("LoadDictionaries-1.bz2142.html"), false, null, null);
    BugzillaLists info = myRetriever.retrieveLists();
    CollectionsCompare c = new CollectionsCompare();
    c.order(new String[]{"CLUtils", "JPF", "SAVANT", "TestProduct", "TyVis", "VESTs", "warped"},
      info.getStringList(BugzillaAttribute.PRODUCT));

    // reverse order:
    c.order(new String[] {"P1", "P2", "P3", "P4", "P5"}, info.getStringList(BugzillaAttribute.PRIORITY));
    c.order(new String[]{"blocker", "critical", "major", "normal", "minor", "trivial", "enhancement"},
      info.getStringList(BugzillaAttribute.SEVERITY));
    c.order(new String[]{
      "All", "Windows 3.1", "Windows 95", "Windows 98", "Windows ME", "Windows 2000", "Windows NT", "Mac System 7",
      "Mac System 7.5", "Mac System 7.6.1", "Mac System 8.0", "Mac System 8.5", "Mac System 8.6", "Mac System 9.0",
      "Linux", "BSDI", "FreeBSD", "NetBSD", "OpenBSD", "AIX", "BeOS", "HP-UX", "IRIX", "Neutrino", "OpenVMS", "OS/2",
      "OSF/1", "Solaris", "SunOS", "other"},
      info.getStringList(BugzillaAttribute.OPERATING_SYSTEM));
    c.order(new String[]{"All", "DEC", "HP", "Macintosh", "PC", "SGI", "Sun", "Other"},
      info.getStringList(BugzillaAttribute.PLATFORM));
    c.order(new String[]{"FIXED", "INVALID", "WONTFIX", "LATER", "REMIND", "DUPLICATE", "WORKSFORME", "MOVED", "---"},
      info.getStringList(BugzillaAttribute.RESOLUTION));
    c.order(new String[]{"UNCONFIRMED", "NEW", "ASSIGNED", "REOPENED", "RESOLVED", "VERIFIED", "CLOSED"},
      info.getStringList(BugzillaAttribute.STATUS));
  }
}
