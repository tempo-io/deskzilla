package com.almworks.bugzilla.integration;

import com.almworks.util.collections.Convertor;
import com.almworks.util.text.SmartDateParser;
import org.jetbrains.annotations.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class BugzillaDateUtil {
  private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
  public static final SmartDateParser INCOMING_TIMESTAMP_PARSER = new SmartDateParser(
    "yyyy-MM-dd HH:mm",
    "yyyy-MM-dd HH:mm z",
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd HH:mm:ss z",
    "yyyyMMddHHmmss",
    "yyyy-MM-dd",
    "yy-MM-dd",
    "HH:mm:ss",
    "EEE HH:mm",
    "MM/dd/yy HH:mm",
    "MM/dd/yy HH:mm z",
    "MM/dd/yy HH:mm:ss",
    "MM/dd/yy HH:mm:ss z",
    "yyyy.MM.dd HH:mm:ss").setIgnoreInvalidTimezones(true);

  private static final Date ZERO_DATE = new Date(0);
  private static final DateFormat PRECISE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
  private static final DateFormat MINUTE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
  public static final DateFormat CUSTOM_DATE_FIELD_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
  public static final DateFormat DATE_FIELD_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

  public static final Convertor<String, Date> DEFAULT_DATE_PARSER = new ParseConvertor(null, null);

  static {
    TimeZone tz = TimeZone.getDefault();
    PRECISE_FORMAT.setTimeZone(tz);
    MINUTE_FORMAT.setTimeZone(tz);
    CUSTOM_DATE_FIELD_FORMAT.setTimeZone(tz);
    DATE_FIELD_FORMAT.setTimeZone(tz);
  }

  @NotNull
  public static Date parseOrWarn(String date, @Nullable TimeZone defaultTimezone) {
    return INCOMING_TIMESTAMP_PARSER.parse(date, ZERO_DATE, defaultTimezone, "unparseable timestamp");
  }

  public static Date parseOrWarn(String date, Date defaultDate, @Nullable TimeZone defaultTimezone) {
    return INCOMING_TIMESTAMP_PARSER.parse(date, defaultDate, defaultTimezone, "unparseable timestamp");
  }

  public static Date parse(String date, @Nullable TimeZone defaultTimezone) {
    return INCOMING_TIMESTAMP_PARSER.parse(date, null, defaultTimezone);
  }

  public static Date parse(String date, Date defaultDate, @Nullable TimeZone defaultTimezone) {
    return INCOMING_TIMESTAMP_PARSER.parse(date, defaultDate, defaultTimezone);
  }

  public static synchronized String format(Date date) {
    boolean precise = (date.getTime() % 60000) != 0;
    DateFormat format = precise ? PRECISE_FORMAT : MINUTE_FORMAT;
    return format.format(date);
  }

  public static synchronized String formatPrecise(Date date) {
    return PRECISE_FORMAT.format(date);
  }

  private static class ParseConvertor extends Convertor<String, Date> {
    private final Date myDefaultDate;
    private final TimeZone myDefaultTimeZone;

    public ParseConvertor(Date defaultDate, @Nullable TimeZone timeZone) {
      myDefaultDate = defaultDate;
      myDefaultTimeZone = timeZone;
    }

    @Override
    public Date convert(String value) {
      return INCOMING_TIMESTAMP_PARSER.parse(value, myDefaultDate, myDefaultTimeZone, "unparseable timestamp");
    }
  }
}