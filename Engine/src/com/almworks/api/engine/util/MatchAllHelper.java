package com.almworks.api.engine.util;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import org.almworks.util.StringUtil;

/**
 * @author dyoma
 */
public class MatchAllHelper {
  private final StringUtil.FindAnyMethod myFindAny;

  public MatchAllHelper(char[][] wordChars) {
    myFindAny = new StringUtil.FindAnyMethod(wordChars, true);
  }

  public boolean matchAttr(long item, DBAttribute<String> attr, DBReader reader) {
    String value = attr.getValue(item, reader);
    if (value == null) {
      return false;
    }
    return matchString(value);
  }

  public boolean matchString(String s) {
    if (s == null)
      return false;
    char[] chars = s.toCharArray();
    int changed = myFindAny.perform(chars, false);
    return changed > 0 && myFindAny.areAllFound();
  }
}
