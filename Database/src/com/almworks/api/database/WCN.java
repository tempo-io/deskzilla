package com.almworks.api.database;

import com.almworks.api.universe.Universe;
import com.almworks.util.collections.Containers;
import org.jetbrains.annotations.*;

public final class WCN implements Comparable {
  public static final WCN EARLIEST = createWCN(Universe.BIG_BANG);
  public static final WCN LATEST = createWCN(Universe.END_OF_THE_UNIVERSE);
  public static final WCN.Range ETERNITY = createRange(EARLIEST, LATEST);
  public static final WCN.Range NIL = createRange(EARLIEST, EARLIEST);

  private final long myUCN;

  private WCN(long UCN) {
    if (UCN < 0)
      throw new IllegalArgumentException("UCN = " + UCN);
    myUCN = UCN;
  }

  public long getUCN() {
    return myUCN;
  }

  public int compareTo(Object o) {
    return Containers.compareLongs(myUCN, ((WCN) o).myUCN);
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof WCN))
      return false;
    return myUCN == ((WCN) obj).myUCN;
  }

  public int hashCode() {
    return longHash(myUCN);
  }

  public String toString() {
    if (myUCN == Universe.BIG_BANG)
      return "--";
    else if (myUCN == Universe.END_OF_THE_UNIVERSE)
      return "++";
    else
      return Long.toString(myUCN);
  }

  public static WCN createWCN(long UCN) {
    return new WCN(UCN);
  }

  @Nullable(documentation = "when both are null")
  public static WCN max(@Nullable WCN wcn1, @Nullable WCN wcn2) {
    if (wcn1 == null) return wcn2;
    if (wcn2 == null) return wcn1;
    return wcn1.getUCN() >= wcn2.getUCN() ? wcn1 : wcn2;
  }

  public static Range createRange(WCN start, WCN end) {
    return new Range(start.getUCN(), end.getUCN());
  }

  public static Range createRange(long start, long end) {
    return new Range(start, end);
  }

  public static DividedRange createDividedRange(WCN start, WCN end, WCN divisor) {
    return new DividedRange(start.getUCN(), end.getUCN(), divisor.getUCN());
  }

  public static DividedRange divideRange(Range range, WCN divisor) {
    long divisorUCN = divisor.getUCN();
    long startUCN = range.getStartUCN();
    long endUCN = range.getEndUCN();
    if (divisorUCN < startUCN)
      divisorUCN = startUCN;
    if (divisorUCN > endUCN)
      divisorUCN = endUCN;
    return new DividedRange(startUCN, endUCN, divisorUCN);
  }

  public static Range intersect(Range range1, Range range2) {
    long maxStart = Math.max(range1.myStart, range2.myStart);
    long minEnd = Math.min(range1.myEnd, range2.myEnd);
    if (maxStart >= minEnd)
      return NIL;
    else
      return new Range(maxStart, minEnd);
  }

  public boolean isEarlier(WCN wcn) {
    return compareTo(wcn) < 0;
  }

  public boolean isEarlierOrEqual(WCN wcn) {
    return compareTo(wcn) <= 0;
  }

  public WCN stepBack() {
    return myUCN <= 0 ? this : new WCN(myUCN - 1);
  }

  public static class Range {
    protected final long myStart;
    protected final long myEnd;

    private Range(long start, long end) {
      if (start > end)
        throw new IllegalArgumentException("start = " + start + "; end = " + end);
      myStart = start;
      myEnd = end;
    }

    public WCN getStart() {
      return new WCN(myStart);
    }

    public WCN getEnd() {
      return new WCN(myEnd);
    }

    public long getStartUCN() {
      return myStart;
    }

    public long getEndUCN() {
      return myEnd;
    }

    public int hashCode() {
      if (isNil())
        return 0; // all NILs are equal
      else
        return longHash(myStart) * 97 + longHash(myEnd);
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof Range))
        return false;
      Range another = (Range) obj;
      if (isNil())
        return another.isNil();
      return myEnd == another.myEnd && myStart == another.myStart;
    }

    public boolean isNil() {
      return myStart >= myEnd;
    }

    public String toString() {
      return "[" + myStart + ":" + myEnd + ")";
    }

    public boolean contains(WCN wcn) {
      if (isNil())
        return false;
      long ucn = wcn.getUCN();
      return myStart <= ucn && ucn < myEnd;
    }
  }

  public static class DividedRange extends Range {
    private final long myDivisor;

    private DividedRange(long start, long end, long divisor) {
      super(start, end);
      if (divisor < start || divisor > end)
        throw new IllegalArgumentException("start = " + start + "; end = " + end + "; divisor = " + divisor);
      myDivisor = divisor;
    }

    public WCN getDivisor() {
      return new WCN(myDivisor);
    }

    public Range getFirstRange() {
      return new Range(myStart, myDivisor);
    }

    public Range getSecondRange() {
      return new Range(myDivisor, myEnd);
    }

    public int hashCode() {
      if (isNil())
        return 0; // all NILs are equal
      return super.hashCode() * 97 + longHash(myDivisor);
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof DividedRange))
        return false;
      DividedRange another = (DividedRange) obj;
      if (isNil())
        return another.isNil();
      return myEnd == another.myEnd && myStart == another.myStart && myDivisor == another.myDivisor;
    }
  }

  public static int longHash(long value) {
    return (int) (value ^ (value >>> 32));
  }
}
