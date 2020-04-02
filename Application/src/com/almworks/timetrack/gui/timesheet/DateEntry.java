package com.almworks.timetrack.gui.timesheet;

public class DateEntry {
  private final long myStart;
  private final long myEnd;
  private final String myName;
  /**
   * days - level 0
   * weeks - level 1
   * months - level 2
   * years - level 3
   */
  private final int myLevel;
  private final boolean myWeekend;

  private int myWeekOffset;

  public DateEntry(long start, long end, String name, int level, boolean weekend) {
    myStart = start;
    myEnd = end;
    myName = name;
    myLevel = level;
    myWeekend = weekend;
  }

  public long getStart() {
    return myStart;
  }

  public long getEnd() {
    return myEnd;
  }

  public String getName() {
    return myName;
  }

  public int getLevel() {
    return myLevel;
  }

  public boolean isWeekend() {
    return myWeekend;
  }

  @Override
  public String toString() {
    return myName;
  }

  public int getWeekOffset() {
    return myWeekOffset;
  }

  public void setWeekOffset(int weekOffset) {
    myWeekOffset = weekOffset;
  }
}
