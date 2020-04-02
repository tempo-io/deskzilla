package com.almworks.bugzilla.provider;

import org.almworks.util.Collections15;
import org.almworks.util.Util;

import java.util.Iterator;
import java.util.SortedMap;

/**
 * :todoc:
 *
 * @author sereda
 */
public class KeywordsTool {
  public static String normalizeString(String value) {
    if (value == null)
      return null;
    return composeString(splitString(value));
  }

  /**
   * see what splitString() returns
   */
  public static String composeString(SortedMap<String, String> keywords) {
    if (keywords.size() == 0)
      return "";
    StringBuffer result = new StringBuffer();
    String infix = null;
    for (Iterator<String> ii = keywords.values().iterator(); ii.hasNext();) {
      String keyword = ii.next();
      if (keyword == null || keyword.length() == 0)
        continue;
      if (infix == null)
        infix = " ";
      else
        result.append(infix);
      result.append(keyword);
    }
    return result.toString();
  }

  /**
   * Returns sorted map, where the key is uppercased keyword and value is the keyword in its original form.
   */
  public static SortedMap<String, String> splitString(String value) {
    if (value == null || value.trim().length() == 0)
      return Collections15.emptySortedMap();
    String[] keywords = value.split("[\\s,]+");
    SortedMap<String, String> map = Collections15.treeMap();
    for (int i = 0; i < keywords.length; i++) {
      String k = keywords[i];
      if (k != null && k.length() > 0)
        map.put(Util.upper(k), k);
    }
    return map;
  }
}
