package com.almworks.bugzilla.integration;

import com.almworks.util.tests.BaseTestCase;

import java.text.SimpleDateFormat;
import java.util.*;

public class BugzillaDateUtilTests extends BaseTestCase {
  public void testRetention() {
    Date date1 = getPreciseTime();
    String s = BugzillaDateUtil.format(date1);
    Date date2 = BugzillaDateUtil.parse(s, null);
    assertEquals(date1, date2);

    date1 = getRawTime();
    s = BugzillaDateUtil.format(date1);
    date2 = BugzillaDateUtil.parse(s, null);
    assertEquals(date1, date2);
  }

  private Date getPreciseTime() {
    long t = System.currentTimeMillis();
    if (t % 60000 == 0)
      t += 9000;
    return new Date(t - (t % 1000));
  }

  private Date getRawTime() {
    long t = System.currentTimeMillis();
    return new Date(t - (t % 60000));
  }

  public void testNonStandardTimes() {
    checkDate("2005-10-04 11:20 eastern", "US/Eastern", "2005-10-04 11:20 EDT");
    checkDate("2005-01-04 11:20 eAsTeRn", "US/Eastern", "2005-01-04 11:20 EST");
    checkDate("2005-07-04 11:20 us/eastern", "US/Eastern", "2005-07-04 11:20 EDT");
  }

  public void testAustralia() {
    checkDate("2005-11-23 14:26 AEST", "GMT", "2005-11-23 03:26 GMT");
    checkDate("2005-06-23 14:26 AEST", "GMT", "2005-06-23 04:26 GMT");
  }

  public void testGMTPlusEight() {
    checkDate("2005-01-30 22:52 GMT+8", "GMT", "2005-01-30 14:52 GMT");
    checkDate("2005-01-30 22:52 GMT+8:30", "GMT", "2005-01-30 14:22 GMT");
    checkDate("2005-01-30 22:52 GMT-0230", "GMT", "2005-01-31 01:22 GMT");
    checkDate("2005-01-30 22:52 GMT-1", "GMT", "2005-01-30 23:52 GMT");
  }

  public void testTwoSymbolTimes() {
    checkDate("2005-10-04 11:20 ET", "US/Eastern", "2005-10-04 11:20 EDT");
    checkDate("2005-01-04 11:20 et", "US/Eastern", "2005-01-04 11:20 EST");
    checkDate("2005-10-04 11:20 MT", "US/Mountain", "2005-10-04 11:20 MDT");
    checkDate("2005-01-04 11:20 mt", "US/Mountain", "2005-01-04 11:20 MST");
  }

  private void checkDate(String raw, String timezone, String parsed) {
    Date date = BugzillaDateUtil.parse(raw, null);
    assertNotNull(date);
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US);
    TimeZone tz = TimeZone.getTimeZone(timezone);
    format.setTimeZone(tz);
    String s = format.format(date);
    assertEquals(parsed, s);
  }

  public void testGentooTimes() {
    checkDate("2007-05-16 11:46:06 0000", "GMT", "2007-05-16 11:46 GMT");
    checkDate("2007-05-16 11:46:06 0100", "GMT", "2007-05-16 10:46 GMT");
    checkDate("2007-05-16 11:46:06 -0200", "GMT", "2007-05-16 13:46 GMT");
  }
}
