package com.almworks.util.datetime;

import com.almworks.util.collections.Convertor;
import com.almworks.util.text.parser.ParseException;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateUtil {
  // specifically locale-dependent formats
  public static final DateFormat LOCAL_TIME = DateFormat.getTimeInstance(DateFormat.SHORT);
  public static final DateFormat LOCAL_DATE = DateFormat.getDateInstance(DateFormat.SHORT);
  public static final DateFormat LOCAL_DATE_TIME = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

  // used for textual messages in some places
  public static final DateFormat US_MONTH_DAY = new SimpleDateFormat("MMM dd", Locale.US);
  public static final DateFormat US_MEDIUM = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US);
  public static final DateFormat US_HOURS_MINUTES = new SimpleDateFormat("HH:mm", Locale.US);

  public static final Set<String> MINUTES = Collections15.hashSet("m", "min", "mins", "minute", "minutes");
  public static final Set<String> HOURS = Collections15.hashSet("h", "hr", "hrs", "hour", "hours");
  public static final Set<String> DAYS = Collections15.hashSet("d", "days", "day", "ds");
  public static final Set<String> WEEKS = Collections15.hashSet("w", "wk", "week", "wks", "weeks");

  public static final Convertor<Date, String> TO_LOCAL_DATE_TIME = new Convertor<Date, String>() {
    public String convert(Date value) {
      //noinspection ConstantConditions
      return value != null ? DateUtil.toLocalDateTime(value) : null;
    }
  };
  private static final String DATE_NOT_AVAILABLE = "n/a";

  public static String toLocalDateTime(@NotNull Date date) {
    // new date to save original date from modification
    return LOCAL_DATE_TIME.format(new Date(date.getTime()));
  }

  public static String toLocalDate(@NotNull Date date) {
    // new date to save original date from modification
    return LOCAL_DATE.format(new Date(date.getTime()));
  }

  public static String toLocalDateOrTime(@NotNull Date value) {
    long millis = value.getTime();
    DateFormat format = isSameDay(millis, System.currentTimeMillis()) ? LOCAL_TIME : LOCAL_DATE;
    return format.format(new Date(millis));
  }

  @NotNull
  public static String toLocalDateAndMaybeTime(@NotNull Date value, @Nullable TimeZone tz) {
    if (value == null)
      return "";
    if (tz == null)
      tz = TimeZone.getDefault();
    assert tz != null;
    long dateUtc = value.getTime();
    int offset = tz.getOffset(dateUtc);
    long timeLocal = (dateUtc + offset) % Const.DAY;
    DateFormat format = timeLocal == 0 ? LOCAL_DATE : LOCAL_DATE_TIME;
    return format.format(new Date(dateUtc));
  }

  public static String toFriendlyDateTime(@Nullable Date date) {
    return toFriendlyDateTime(date, null, false);
  }

  public static String toFriendlyDateTime(@Nullable Date date, @Nullable DateFormat fullFormat, boolean ago) {
    if (date == null)
      return DATE_NOT_AVAILABLE;
    long millis = date.getTime();
    if (millis <= 0)
      return DATE_NOT_AVAILABLE;

    long now = System.currentTimeMillis();
    long diff = now - millis;
    if (ago && diff >= -30 * Const.SECOND) {
      if (diff < Const.MINUTE) {
        return "<1 min ago";
      } else if (diff < Const.HOUR) {
        int mins = (int) (diff / Const.MINUTE);
        return mins + " min ago";
      } else if (diff < Const.DAY) {
        int hrs = (int) (diff / Const.HOUR);
        return hrs + (hrs > 1 ? " hrs" : " hr") + " ago";
      }
    }
    // using temp to avoid changing date
    Date temp = new Date(millis);
    if (isSameDay(millis, now))
      return "today at " + LOCAL_TIME.format(temp);
    else
      return "on " + Util.NN(fullFormat, LOCAL_DATE_TIME).format(temp);
  }

  public static boolean isSameDay(long millisA, long millisB) {
    TimeZone zone = TimeZone.getDefault();
    long a = millisA + zone.getOffset(millisA);
    long b = millisB + zone.getOffset(millisB);
    return a / Const.DAY == b / Const.DAY;
  }

  public static String getFriendlyDuration(int seconds, boolean showZeroHours) {
    return getFriendlyDuration(seconds, showZeroHours, false);
  }

  public static String getFriendlyDuration(int seconds, boolean showZeroHours, boolean twoDigitMinutes) {
    if(seconds < 0) {
      return "";
    }

    final int hours = seconds / 3600;
    final int minutes = (seconds % 3600) / 60;
    if(hours == 0 && minutes == 0) {
      return "0h";
    }

    final StringBuilder b = new StringBuilder();

    if(hours > 0 || showZeroHours) {
      b.append(hours);
      b.append('h');
    }

    if(minutes > 0 || twoDigitMinutes) {
      if(b.length() > 0) {
        b.append(' ');
      }
      if(twoDigitMinutes && minutes < 10) {
        b.append('0');
      }
      b.append(minutes);
      b.append('m');
    }
    
    return b.toString();
  }

  public static String getHoursDurationFixed(int seconds) {
    if (seconds <= 0)
      return "0.0";
    if (seconds < 360)
      return "<0.1";
    int hours = seconds / 3600;
    int decihours = (seconds - hours * 3600) / 360;
    return hours + "." + decihours;
  }

  public static String getFriendlyDurationVerbose(int seconds) {
    if (seconds < 0)
      return "";
    if (seconds == 0)
      return "0";
    int s = seconds;
    int days = s / 86400;
    s = s % 86400;
    int hours = s / 3600;
    s = s % 3600;
    int mins = s / 60;
    StringBuilder b = new StringBuilder();
    if (days > 0) {
      b.append(days).append('d');
    }
    if (hours > 0 || (days > 0 && mins > 0)) {
      if (b.length() > 0)
        b.append(' ');
      b.append(hours).append('h');
    }
    if (mins > 0) {
      if (b.length() > 0)
        b.append(' ');
      b.append(mins).append('m');
    }
    return b.toString();
  }

  /**
   * Returns timestamp of the 0:00 time in the given timezone on the same day as t
   */
  public static long truncDay(long t, TimeZone tz) {
    final Calendar c = Calendar.getInstance(tz);
    c.setTimeInMillis(t);
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    final long r = c.getTimeInMillis();
    assert r - t <= 3 * Const.HOUR;
    assert t - r < 27 * Const.HOUR;
    return r;
  }

  public static int parseDuration(String s, boolean allowZero) throws ParseException {
    if (s == null)
      throw new ParseException("null");
    List<Object> input = groupDurationInput(s);
    if (input.isEmpty())
      throw new ParseException("no data");
    int totalSeconds = 0;
    BigDecimal n = null;
    int mask = 0;
    for (Object o : input) {
      if (o instanceof BigDecimal) {
        if (n != null) throw new ParseException("two numbers in a row " + n + " " + o);
        n = (BigDecimal) o;
      } else if (o instanceof String) {
        if (n == null) throw new ParseException("no number for " + o);
        String unit = (String) o;
        int multiplier;
        if (MINUTES.contains(unit)) {
          if ((mask & 1) != 0) throw new ParseException("minutes?");
          mask |= 1;
          multiplier = 60;
        } else if (HOURS.contains(unit)) {
          if ((mask & 2) != 0) throw new ParseException("hours?");
          mask |= 2;
          multiplier = 3600;
        } else if (DAYS.contains(unit)) {
          if ((mask & 4) != 0) throw new ParseException("days?");
          mask |= 4;
          multiplier = 3600 * 8;
        } else if (WEEKS.contains(unit)) {
          if ((mask & 8) != 0) throw new ParseException("weeks?");
          mask |= 8;
          multiplier = 3600 * 8 * 5;
        } else {
          throw new ParseException("what's " + unit + "?");
        }
        totalSeconds += n.multiply(new BigDecimal(multiplier)).intValue();
        n = null;
      } else {
        assert false : o;
      }
    }
    if (n != null) {
      if (input.size() == 1) {
        // hours by default
        totalSeconds += n.multiply(new BigDecimal(3600)).intValue();
      } else {
        throw new ParseException(n + " what?");
      }
    }
    if (totalSeconds < 0) {
      throw new ParseException(s);
    }
    if (totalSeconds < 60 && !allowZero) {
      throw new ParseException(s);
    }
    return totalSeconds;
  }

  private static List<Object> groupDurationInput(String s) throws ParseException {
    s = s.replace(',', '.').toLowerCase(Locale.US);
    List<Object> input = Collections15.arrayList(6);
    int lasttype = 0;
    int from = 0;
    int len = s.length();
    for (int i = 0; i <= len; i++) {
      char c = i == len ? 0 : s.charAt(i);
      int type;
      if (Character.isWhitespace(c) || c == 0) {
        type = 0;
      } else if (Character.isDigit(c) || c == '.') {
        type = 1;
      } else {
        type = -1;
      }
      if (type != lasttype) {
        if (lasttype < 0) {
          input.add(s.substring(from, i));
        } else if (lasttype > 0){
          String n = s.substring(from, i);
          try {
            input.add(new BigDecimal(n));
          } catch (NumberFormatException e) {
            throw new ParseException("cannot parse " + n);
          }
        }
        from = i;
        lasttype = type;
      }
    }
    return input;
  }

  /**
   * returns seconds
   */
  public static int parseDurationOld(String s, boolean allowZero) throws ParseException {
    if (s == null)
      throw new ParseException("null");
    try {
      Pattern p = Pattern.compile("\\s*([0-9\\.,]*)\\s*(h[a-z]*)?\\s*([0-9]*)\\s*(m[a-z]*)?\\s*");
      Matcher m = p.matcher(s);
      if (!m.matches())
        throw new ParseException(s);

      String n1 = m.group(1);
      String n2 = m.group(3);
      boolean hoursMatched = m.group(2) != null;
      boolean minsMatched = m.group(4) != null;

      if (n1.length() == 0 && n2.length() == 0)
        throw new ParseException(s);
      n1 = n1.replace(',', '.');

      if (n1.indexOf('.') >= 0 && n2.length() != 0)
        throw new ParseException(s + " (fractional hours and minutes)");

      if (n1.length() > 0 && !hoursMatched && n2.length() > 0)
        throw new ParseException(s);

      if (minsMatched && !hoursMatched) {
        // only minutes
        n2 = n1;
        n1 = "";
      }

      int seconds = 0;

      if (n1.length() > 0) {
        BigDecimal dec = new BigDecimal(n1);
        seconds += dec.multiply(new BigDecimal(3600)).intValue();
      }

      if (n2.length() > 0) {
        int minutes = Integer.parseInt(n2);
        if (minutes >= 60)
          throw new ParseException(s);
        seconds += minutes * 60;
      }

      if (seconds < 0) {
        throw new ParseException(s);
      }
      if (seconds < 60 && !allowZero) {
        throw new ParseException(s);
      }
      return seconds;
    } catch (NumberFormatException e) {
      throw new ParseException(s);
    } catch (ArithmeticException e) {
      throw new ParseException(s);
    }
  }
}
