package com.almworks.util.datetime;

import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.Collections15;
import org.almworks.util.Const;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Vasya
 */
public class DateUtilTests extends BaseTestCase {
  private final long D = Const.DAY, H = Const.HOUR, C = 100*365*D, M = 10*C;

  public void testFriendlyView() {
    DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    assertEquals("n/a", DateUtil.toFriendlyDateTime(null));
    assertEquals("n/a", DateUtil.toFriendlyDateTime(new Date(0)));
    assertEquals("<1 min ago",
      DateUtil.toFriendlyDateTime(new Date(now() - 59 * 1000), format, true));
    assertEquals("4 min ago",
      DateUtil.toFriendlyDateTime(new Date(now() - 299 * 1000), format, true));
    assertEquals("5 min ago",
      DateUtil.toFriendlyDateTime(new Date(now() - 300 * 1000), format, true));
    assertEquals("5 min ago",
      DateUtil.toFriendlyDateTime(new Date(now() - 301 * 1000), format, true));
    assertEquals("59 min ago",
      DateUtil.toFriendlyDateTime(new Date(now() - 3599900), format, true));
    assertEquals("1 hr ago",
      DateUtil.toFriendlyDateTime(new Date(now() - 3600000), format, true));
    assertEquals("1 hr ago",
      DateUtil.toFriendlyDateTime(new Date(now() - 3600001), format, true));
    assertEquals("1 hr ago",
      DateUtil.toFriendlyDateTime(new Date(now() - 7199900), format, true));
    assertEquals("2 hrs ago",
      DateUtil.toFriendlyDateTime(new Date(now() - 7200000), format, true));
    assertEquals("2 hrs ago",
      DateUtil.toFriendlyDateTime(new Date(now() - 7200001), format, true));
    assertEquals("23 hrs ago",
      DateUtil.toFriendlyDateTime(new Date(now() - 24 * 3600000 + 1000), format, true));
    Date date = new Date(now() - 24 * 3600000);
    assertEquals("on " + format.format(date), DateUtil.toFriendlyDateTime(date, format, true));
//    assertEquals("today at " + DateUtil.LOCAL_TIME.format(date), DateUtil.toFriendlyDateTime(new Date(now()), format, false));
  }

  private long now() {
    return System.currentTimeMillis();
  }

  public void testTruncDay() {
    // Tries to verify that for all times t, for all timezones tz:
    // 1) truncDay(t, tz) - t <= 3 hours
    // 2) t - truncDay(t, tz) < 27 hours
    final long now = now();

    final long[] bases =
      { -now-5*M, -now-M, -now-C, -now, -now+182*D, -365*D, -D, 0, D, 365*D, now, now+182*D, now+C, now+M, now+5*M };

    for(final long base : bases) {
      for(long offset = -14*H; offset <= 14*H; offset += H) {
        final long t = base + offset;
        final Set<Integer> testedTimezones = Collections15.hashSet();
        for(final String id : TimeZone.getAvailableIDs()) {
          final TimeZone tz = TimeZone.getTimeZone(id);
          if(testedTimezones.add(tz.getOffset(t))) {
            check(t, tz);
          }
        }
      }
    }
  }

  private void check(long t, TimeZone tz) {
    final long s = DateUtil.truncDay(t, tz);
    assertTrue("truncDay(t) > t for t = " + new Date(t) + ", s = " + new Date(s) + ", tz = " + tz.getID(), s-t <= 3*H);
    assertTrue("t - truncDay(t) >= 1 day for t = " + new Date(t) + ", s = " + new Date(s) + ", tz = " + tz.getID(), t-s < D+3*H);
  }
}
