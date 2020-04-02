package com.almworks.bugzilla.provider.datalink.flags2.columns;

import org.almworks.util.Util;

import java.util.Comparator;
import java.util.List;

import static org.almworks.util.Util.compareInts;

/**
 * In comments to methods in this class, x is any positive number (x > 0).
 * For columns containing flags with only one status, sort order is simply by number of flags, then by flag names.
 * Otherwise, comparator imposes ordering on bug (perceived as a collection of flags) with the following property.
 * Bugs appear grouped by statuses in such way that there are:
 * - a contiguous sublist with bugs having flags with '?' status,
 * - a contiguous sublist with bugs having flags with '-' status, and
 * - two contiguous sublists with bugs having flags with '+' status.
 * More specifically, a list will look like this (bug <-> line, column <-> number of flags with this state):
 * ? + -
 * -----
 * x 0 0 -----------------------------------|
 * x x 0 -| 1st region of bugs with '+'     | all bugs with '?'
 * x x x -|---------------------------------|--------------------|
 * x 0 x ___________________________________|                    | all bugs with '-'
 * 0 0 x                                                         |
 * 0 x x -| 2nd region of bugs with '+'__________________________|
 * 0 x 0 -|
 * 0 0 0
 * Within groups, flag collections are sorted:
 * 1) by number of '?', then of '+', then of '-';
 * 2) by names.
 */
abstract class FlagsComparator<T> implements Comparator<List<? extends T>> {
  @Override
  public int compare(List<? extends T> o1, List<? extends T> o2) {
    if (isSingleStatus()) {
      return compareByStatus(o1, o2);
    }
    return compareGrouped(o1, o2);
  }

  protected boolean isSingleStatus() {
    return false;
  }

  protected int compareByStatus(List<? extends T> o1, List<? extends T> o2) {
    assert false;
    return 0;
  }

  private int compareGrouped(List<? extends T> o1, List<? extends T> o2) {
    int req1 = countFlags(o1, '?', false);
    int req2 = countFlags(o2, '?', false);
    int cmp = -cmp0(req1, req2);
    if (cmp == 0) {
      int plus1 = countFlags(o1, '+', false);
      int plus2 = countFlags(o2, '+', false);
      int plusDiff = cmp0(plus1, plus2);
      int minus1 = countFlags(o1, '-', false);
      int minus2 = countFlags(o2, '-', false);
      int minusDiff = cmp0(minus1, minus2);

      if (plusDiff > 0) {
        cmp = -cmp0(minus2);
      } else if (plusDiff < 0) {
        cmp = cmp0(minus1);
      } else {
        cmp = minusDiff;
      }

      // if no requests, inverse results
      cmp *= cmp0(req1);

      if (cmp == 0) {
        // compare inside the group
        if (req1 > 0) {
          cmp = compareCount(o1, o2, '?', true);
        }
        if (cmp == 0) cmp = -compareInts(req1, req2);
        if (cmp == 0) cmp = -compareInts(plus1, plus2);
        if (cmp == 0) cmp = -compareInts(minus1, minus2);
        if (cmp == 0) cmp = compareNames(o1, o2);
      }
    }
    return cmp;
  }

  protected int compareNames(List<? extends T> o1, List<? extends T> o2) {
    return 0;
  }

  /**
   * (0, x) => -1
   * (x, 0) => 1
   * otherwise 0
   */
  private int cmp0(int n1, int n2) {
    if (n1 > 0 && n2 == 0) return 1;
    if (n1 == 0 && n2 > 0) return -1;
    return 0;
  }

  /**
   * 0 => -1
   * x => 1
   */
  private int cmp0(int n) {
    return n == 0 ? -1 : 1;
  }

  protected final int compareCount(List<? extends T> o1, List<? extends T> o2, char status, boolean accountForMe) {
    int count1 = countFlags(o1, status, accountForMe);
    int count2 = countFlags(o2, status, accountForMe);
    return -Util.compareInts(count1, count2);
  }

  protected abstract int countFlags(List<? extends T> flags, char status, boolean accountForMe);
}
