package com.almworks.bugzilla;

import com.almworks.util.i18n.*;

import javax.swing.*;

public class BugzillaBook extends PropertyFileBasedTextBook {
  private static final BugzillaBook BOOK = new BugzillaBook();

  private BugzillaBook() {
    super("com.almworks.rc.bugzilla.BugzillaBook");
  }

  public static void replaceText(String prefix, JComponent root) {
    BOOK.doReplaceText(prefix, root);
  }

  public static LText text(String key, String defaultValue) {
    return BOOK.createText(key, defaultValue);
  }

  public static <T1> LText1<T1> text(String key, String defaultValue, T1 sample1) {
    return BOOK.<T1>text1(key, defaultValue);
  }

  public static <T1, T2> LText2<T1, T2> text(String key, String defaultValue, T1 sample1, T2 sample2) {
    return BOOK.<T1, T2>text2(key, defaultValue);
  }
}
