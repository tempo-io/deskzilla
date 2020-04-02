package com.almworks.bugzilla.integration;

import com.almworks.bugzilla.integration.data.BooleanChart;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;

import static com.almworks.util.collections.Functional.infiniteSaturated;

/**
 * :todoc:
 *
 * @author sereda
 */
public class QueryURLBuilder implements QueryURL.Changeable {
  private static final long MIN_CHANGE_TIME = 1 * 24 * 60 * 60 * 1000 + 1;
  private static final String DEFAULT_ENCODING = "UTF-8";//may be use here Util.getDefaultCharsetName()?
  final static String SCRIPT_NAME = "buglist.cgi";

  private final List<String> myConditions = Collections15.arrayList();
  private final List<BooleanChart> myCharts = Collections15.arrayList();
  private static final int ORDER_BY = 0;
  private static final int LIMIT = 1;
  private static final int OFFSET = 2;
  private String[] myAdditionalClauses = new String[3]; 
  private final String myCharset;
  @Nullable
  private final String myTimeZone;

  public static QueryURLBuilder createDefault() {
    return new QueryURLBuilder(DEFAULT_ENCODING, null);
  }

  public QueryURLBuilder(String charset, @Nullable String timeZone) {
    this.myCharset = charset != null ? charset : DEFAULT_ENCODING;
    myTimeZone = timeZone;
  }

  public void addProductCondition(String[] products) {
    for (int i = 0; i < products.length; i++) {
      myConditions.add("product=" + encode(products[i])); // todo refactor generalize
    }
  }

  private String encode(String text) {
    try {
      return URLEncoder.encode(text, myCharset);
    } catch (UnsupportedEncodingException e) {
      Log.warn("", e);
      return text;
    }
  }

  public void addChangeDateCondition(long changedSince) {
    if (changedSince < MIN_CHANGE_TIME)
      changedSince = MIN_CHANGE_TIME;
    myConditions.add("chfieldfrom=" + BugzillaHTMLConstants.getRequestDateFormat(myTimeZone).format(new Date(changedSince)));
    myConditions.add("chfieldto=Now");
  }

  @Override
  public void setOrderBy(Column... columns) {
    StringBuilder clause = new StringBuilder("order=");
    Iterator<String> sep = infiniteSaturated("", "%2C").iterator();
    String descStr = "%20DESC";
    for (Column column : columns) clause
      .append(sep.next())
      .append(getQueryColumn(column))
      .append(column.isDesc() ? descStr : "");
    myAdditionalClauses[ORDER_BY] = clause.toString();
  }

  private static String getQueryColumn(Column column) {
    switch (column) {
    case ID:
    case ID_DESC:
      return "bugs.bug_id";
    case MODIFICATION_DATE:
    case MODIFICATION_DATE_DESC:
      return "bugs.delta_ts";
    }
    assert false : column;
    return "bugs.bug_id";
  }

  public boolean isLimitSet() {
    return myAdditionalClauses[LIMIT] != null;
  }

  public void setLimit(int limit) {
    assert limit >= 0 : limit;
    myAdditionalClauses[LIMIT] = "limit=" + limit;
  }
  
  public void setOffset(int offset) {
    assert offset >= 0 : offset;
    if (offset > 0) myAdditionalClauses[OFFSET] = "offset=" + offset;
    else myAdditionalClauses[OFFSET] = null;
  }

  public void addBooleanChart(@Nullable BooleanChart chart) {
    if (chart != null && !chart.isEmpty())
      myCharts.add(chart);
  }

  public String getURL() throws IllegalStateException {
//    if (myConditions.isEmpty() && myCharts.isEmpty())
//      throw new IllegalStateException("Bad state: no conditions, no charts");
    StringBuffer result = new StringBuffer(SCRIPT_NAME);
    String prefix = "?";
    for (String condition : myConditions) {
      result.append(prefix).append(condition);
      prefix = "&";
    }
    final StringBuffer urlPartFromCharts = getURLPartFromCharts();
    if (urlPartFromCharts.length() > 0) {
      result.append(prefix).append(urlPartFromCharts);
      prefix = "&";
    }
    if (result.length() == SCRIPT_NAME.length()) {
      assert "?".equals(prefix) : result;
      // http://jira.almworks.com/browse/DZO-618 - Bugzilla rejects empty search
      // make up a search condition that's always true
      result.append(prefix).append("field0-0-0=bug_id&type0-0-0=notequals&value0-0-0=0");
      prefix = "&";
    }
    assert "&".equals(prefix);
    for (int i = 0; i < myAdditionalClauses.length; ++i) 
      if (myAdditionalClauses[i] != null) 
        result.append(prefix).append(myAdditionalClauses[i]);
    return result.toString();
  }

  private StringBuffer getURLPartFromCharts() {
    StringBuffer result = new StringBuffer();
    for (int i = 0; i < myCharts.size(); i++) {
      BooleanChart booleanChart = myCharts.get(i);
      final List<BooleanChart.Group> groups = booleanChart.getGroups();
      for (int j = 0; j < groups.size(); j++) {
        BooleanChart.Group group = groups.get(j);
        final List<BooleanChart.Element> elements = group.getElements();
        for (int k = 0; k < elements.size(); k++) {
          String s = i + "-" + j + "-" + k + "=";
          BooleanChart.Element element = elements.get(k);
          String attr;
          BugzillaAttribute bugzillaAttribute = element.getField();
          if (bugzillaAttribute != null) {
            attr = BugzillaHTMLConstants.ATTRIBUTE_TO_BOOLEAN_CHART_MAP.get(bugzillaAttribute);
            assert attr != null : bugzillaAttribute;
          } else {
            attr = element.getSpecial();
            assert attr != null : element;
          }
          if (attr == null)
            attr = "nofield"; // todo
          if (result.length() > 0)
            result.append('&');
          result.append("field").append(s).append(encode(attr));
          result.append("&type").append(s).append(encode(element.getType().getName()));
          result.append("&value").append(s).append(encode(element.getValue()));
        }
      }
    }
    return result;
  }
}

