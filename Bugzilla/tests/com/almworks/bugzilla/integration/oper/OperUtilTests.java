package com.almworks.bugzilla.integration.oper;

import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.xml.JDOMUtils;
import org.jdom.*;

public class OperUtilTests extends BaseTestCase {
  public void testLocationOfUpdateFormInMatchConfirmationPage() throws JDOMException {
    Document document = JDOMUtils.parse("<whatever>" +
      "<div id=\"header\">\n" +
      "    <h1>Confirm Match</h1>\n" +
      "\n" +
      "\n" +
      "</div>\n" +
      "\n" +
      "  <form method=\"post\"     action=\"/bz219/process_bug.cgi\"  >\n" +
      "\n" +
      "  <p>Bugzilla cannot make a conclusive match for one or more of the\n" +
      "    names and/or email addresses you entered on the previous page.<br/>\n" +
      "    Please examine the lists of potential matches below and select the\n" +
      "    one you want, or go back to the previous page to revise the names\n" +
      "    you entered.\n" +
      "  </p></form>" +
      "</whatever>");

    Element element = OperUtils.findUpdateFormElement(document.getRootElement(), false);
    assertNotNull(element);
    assertEquals("post", element.getAttributeValue("method"));
  }

  public void testLocationOfUpdateFormInBugPage() throws JDOMException {
    Document document = JDOMUtils.parse("  " +
      "<whatever>" +
      "<script>//-->\n" +
      "  </script>\n" +
      "\n" +
      "<form name=\"changeform\" method=\"post\" action=\"process_bug.cgi\">\n" +
      "\n" +
      "  <input type=\"hidden\" name=\"delta_ts\" value=\"2005-06-10 18:15:57\"/>\n" +
      "  <input type=\"hidden\" name=\"longdesclength\" value=\"0\"/>\n" +
      "  <input type=\"hidden\" name=\"id\" value=\"52\"/>\n" +
      "</form>" +
      "</whatever>");

    Element element = OperUtils.findUpdateFormElement(document.getRootElement(), false);
    assertNotNull(element);
    assertEquals("post", element.getAttributeValue("method"));
  }
}
