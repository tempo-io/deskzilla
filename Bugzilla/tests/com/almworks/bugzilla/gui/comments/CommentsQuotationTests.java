package com.almworks.bugzilla.gui.comments;

import com.almworks.util.files.FileUtil;
import com.almworks.util.tests.BaseTestCase;

/**
 * @author dyoma
 */
public class CommentsQuotationTests extends BaseTestCase {
  public void testRightQuotation() {
    assertEquals("TEXT1", CommentsTreeStructure.getQuotation("\"(In reply to comment #1)\n> text1\ntext2\""));
  }

  public void testEndingQuotation() {
    assertEquals("TEXT", CommentsTreeStructure.getQuotation("line\n> text"));
  }

  public void testBug1() {
    checkNoQuotation("bugzilla.mozilla.org.52821.79.txt");
  }

  public void testBug2() {
    checkNoQuotation("bugzilla.mozilla.org.52821.223.txt");
  }

  public void test() {
    String quotation = getQuotation("bugzilla.mozilla.org.52821.155.txt");
    assertNotNull(quotation);
    String text = getTransformedCommentText("bugzilla.mozilla.org.52821.138.txt");
    System.out.println(quotation);
    System.out.println(text);
    assertTrue(text.indexOf(quotation) != -1);
  }

  private void checkNoQuotation(String commentFile) {
    String quotation = getQuotation(commentFile);
    assertNull(quotation, quotation);
  }

  private String getQuotation(String commentFile) {
    String commentText = loadSampleComment(commentFile);
    String quotation = CommentsTreeStructure.getQuotation(commentText);
    return quotation;
  }

  private String getTransformedCommentText(String commentFile) {
    String commentText = loadSampleComment(commentFile);
    return CommentsTreeStructure.getTransformedCommentText(commentText);
  }

  private String loadSampleComment(String comment) {
    return FileUtil.loadTextResource("com/almworks/bugzilla/gui/comments/" + comment, getClass().getClassLoader());
  }
}
