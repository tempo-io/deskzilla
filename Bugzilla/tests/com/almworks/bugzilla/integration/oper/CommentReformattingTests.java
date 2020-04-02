package com.almworks.bugzilla.integration.oper;

import com.almworks.util.tests.BaseTestCase;

public class CommentReformattingTests extends BaseTestCase {
  public void test() {
    assertEquals(null, OperUtils.reformatComment(null));
    assertEquals("", OperUtils.reformatComment(""));
    assertEquals("", OperUtils.reformatComment("   \n   "));

    assertEquals("Comment", OperUtils.reformatComment("Comment"));
    assertEquals("Comment 1\nComment 2", OperUtils.reformatComment("Comment 1\nComment 2"));
    assertEquals("A   B", OperUtils.reformatComment("A   B"));
    assertEquals("A\t\tB", OperUtils.reformatComment("A\t\tB"));
    assertEquals("A", OperUtils.reformatComment("A\n\n\n"));

    assertEquals(mm("123456789 ", 8) + "\n123456789", OperUtils.reformatComment(mm(" 123456789", 9)));

    assertEquals("long long long line long long long line long long long line long long long line \n" +
      "that end\n" +
      "and new line",
      OperUtils.reformatComment(
        " long long long line long long long line long long long line long long long line that end\n" +
      "and new line"
      ));

    assertEquals(mm("0123456789", 60), OperUtils.reformatComment(mm("0123456789", 60)));
    assertEquals("A \n" + mm("0123456789", 8), OperUtils.reformatComment("A " + mm("0123456789", 8)));
    assertEquals(mm("123456789 ", 8) + "\n" + mm(" ", 5) +  "123456789 123456789",
      OperUtils.reformatComment(mm("123456789 ", 8) + mm(" ", 5) + mm("123456789 ", 2)));

    assertEquals("A" + mm(" ", 79) + "\n" + mm(" ", 21) + "B", OperUtils.reformatComment("A" + mm(" ", 100) + "B"));
    assertEquals(mm("0123 56789", 8), OperUtils.reformatComment(mm("0123 56789", 8)));
    assertEquals(mm("0123 56789", 8), OperUtils.reformatComment(mm("0123 56789", 8) + " "));
  }

  private static String mm(String s, int count) {
    StringBuffer r = new StringBuffer(count);
    for (int i = 0; i < count; i++)
      r.append(s);
    return r.toString();
  }
}
