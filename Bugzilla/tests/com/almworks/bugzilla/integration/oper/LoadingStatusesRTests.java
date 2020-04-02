package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.integration.data.StatusInfo;
import com.almworks.util.Pair;
import com.almworks.util.tests.BaseTestCase;
import org.jdom.Document;
import org.jdom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

public class LoadingStatusesRTests extends BaseTestCase {
  private static final String RESOURCE = "com/almworks/bugzilla/integration/oper/LoadedStatusesTest.bz320.html";

  public void test() throws IOException, SAXException {
    Document document = OperTestUtils.loadDocument(RESOURCE);
    Element form = OperUtils.findUpdateFormElement(document.getRootElement(), false);
    assertNotNull(form);
    Pair<List<StatusInfo>,String> pair = LoadFrontPage.findAllowedStatusChanges(form);
    assertNotNull(pair);
    List<StatusInfo> list = pair.getFirst();
    assertEquals(4, list.size());
    assertStatus(list.get(0), "REOPENED", true);
    assertStatus(list.get(1), "NEW", true);
    assertStatus(list.get(2), "RESOLVED", false);
    assertStatus(list.get(3), "VERIFIED", false);
    assertEquals("RESOLVED", pair.getSecond());
  }

  private void assertStatus(StatusInfo statusInfo, String name, Boolean open) {
    assertEquals(name, statusInfo.getStatus());
    assertEquals("for " + name, open, statusInfo.isOpen());
  }
}
