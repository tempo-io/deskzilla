package com.almworks.bugzilla.integration;

import com.almworks.bugzilla.integration.data.BooleanChart;
import com.almworks.bugzilla.integration.data.BooleanChartElementType;
import com.almworks.util.tests.BaseTestCase;

public class URLQueryBuilderTests extends BaseTestCase {
  public void testId() {
    QueryURLBuilder builder = QueryURLBuilder.createDefault();

    final BooleanChart chart = new BooleanChart();
    final BooleanChart.Group group = new BooleanChart.Group();
    group.addElement(BooleanChart.createElement(BugzillaAttribute.ID, BooleanChartElementType.EQUALS, "1"));
    chart.addGroup(group);
    builder.addBooleanChart(chart);
    assertEquals(QueryURLBuilder.SCRIPT_NAME + "?field0-0-0=bug_id&type0-0-0=equals&value0-0-0=1", builder.getURL());
  }

  public void testEmpty() {
    QueryURLBuilder builder = QueryURLBuilder.createDefault();
    assertFalse(builder.getURL(), QueryURLBuilder.SCRIPT_NAME.equals(builder.getURL()));
  }

  public void testBooleanCharts() {


    BooleanChart.Group group1 = new BooleanChart.Group();
    group1.addElement(BooleanChart.createElement(BugzillaAttribute.ID, BooleanChartElementType.LESS, "33"));
    group1.addElement(BooleanChart.createElement(BugzillaAttribute.STATUS, BooleanChartElementType.EQUALS, "OPEN"));

    BooleanChart.Group group2 = new BooleanChart.Group();
    group2.addElement(
      BooleanChart.createElement(BugzillaAttribute.TARGET_MILESTONE, BooleanChartElementType.EQUALS, "---"));
    group2.addElement(BooleanChart.createElement(BugzillaAttribute.TOTAL_VOTES, BooleanChartElementType.GREATER, "0"));

    BooleanChart chart1 = new BooleanChart();
    chart1.addGroup(group1);
    chart1.addGroup(group2);

    BooleanChart chart2 = new BooleanChart();
    chart2.addGroup(group1);
    chart2.addGroup(group2);


    QueryURLBuilder builder = QueryURLBuilder.createDefault();
    builder.addBooleanChart(chart1);
    builder.addBooleanChart(chart2);
    assertEquals(QueryURLBuilder.SCRIPT_NAME +
      "?field0-0-0=bug_id&type0-0-0=lessthan&value0-0-0=33&" +
      "field0-0-1=bug_status&type0-0-1=equals&value0-0-1=OPEN&" +
      "field0-1-0=target_milestone&type0-1-0=equals&value0-1-0=---&" +
      "field0-1-1=votes&type0-1-1=greaterthan&value0-1-1=0&" +
      "field1-0-0=bug_id&type1-0-0=lessthan&value1-0-0=33&" +
      "field1-0-1=bug_status&type1-0-1=equals&value1-0-1=OPEN&" +
      "field1-1-0=target_milestone&type1-1-0=equals&value1-1-0=---&" +
      "field1-1-1=votes&type1-1-1=greaterthan&value1-1-1=0",
      builder.getURL());
  }
}
