package com.almworks.sumtable;

import com.almworks.integers.LongArray;
import com.almworks.items.api.*;
import com.almworks.util.commons.Procedure;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.Bottleneck;
import com.almworks.util.threads.ThreadAWT;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;
import util.concurrent.SynchronizedBoolean;
import util.external.BitSet2;

import java.util.*;

class CountingSummaryTableData extends SummaryTableData {
  private final DBFilter myFilter;
  private final Bottleneck myDelayedRecount;
  private final Procedure<CountingSummaryTableData> myFinish;

  private final boolean myEmpty;

  private int[] myTotals;
  private final SynchronizedBoolean myCancelled = new SynchronizedBoolean(false);
  private final Runnable myCounter = new Counter();

  @ThreadAWT
  public CountingSummaryTableData(List<STFilter> columns, List<STFilter> rows, List<STFilter> counters, @NotNull DBFilter filter, Bottleneck delayedRecount, Procedure<CountingSummaryTableData> finish) {
    super(copyWithTotal(columns), copyWithTotal(rows), Collections15.arrayList(counters));
    myFilter = filter;
    myDelayedRecount = delayedRecount;
    myFinish = finish;
    myEmpty = myCounters.size() == 0;
  }

  public boolean isDataAvailable() {
    return !myEmpty;
  }

  private static List<STFilter> copyWithTotal(List<STFilter> columns) {
    List<STFilter> result = Collections15.arrayList(columns);
    result.add(STFilter.TOTAL);
    return result;
  }

  public void startCounting() {
    if (!myEmpty) {
      ThreadGate.LONG(SummaryTableCounter.class).execute(myCounter);
    } else {
      myFinish.invoke(this);
    }
  }

  @ThreadAWT
  private void setCounted(int[] totals, boolean success) {
    if (myCancelled.get()) {
      return;
    }
    if (!success) {
      // not cancelled but preview is invalidated
      myDelayedRecount.requestDelayed();
      return;
    }
    myTotals = totals;
    myFinish.invoke(this);
  }

  public void cancelCounting() {
    // could be counting
    myCancelled.set(true);
  }

  @Nullable
  @ThreadAWT
  public Integer getCellCount(int counterIndex, int rowIndex, int columnIndex) {
    if (myTotals == null)
      return null;
    if (rowIndex < 0 || columnIndex < 0 || counterIndex < 0)
      return null;
    int counterCount = myCounters.size();
    int rowCount = myRows.size();
    int columnCount = myColumns.size();
    if (counterIndex >= counterCount || rowIndex >= rowCount || columnIndex >= columnCount)
      return null;
    int idx = (counterIndex * rowCount + rowIndex) * columnCount + columnIndex;
    if (idx >= myTotals.length)
      return null;
    return myTotals[idx];
  }


  public SummaryTableData sort(final int colIndex, final int colDirection, int rowIndex, int rowDirection,
    final int counterIndex)
  {
    if (myEmpty)
      return this;
    if (myTotals == null)
      return this;
    if (myRows.size() == 0 || myColumns.size() == 0 || myCounters.size() == 0)
      return this;
    if (counterIndex < 0 || counterIndex >= myCounters.size())
      return this;

    int[] rowPermutation = getSortingPermutation(colIndex, colDirection, counterIndex, true);
    int[] columnPermutation = getSortingPermutation(rowIndex, rowDirection, counterIndex, false);

    if (rowPermutation == null && columnPermutation == null)
      return this;

    return new SortingSummaryTableData(this, rowPermutation, columnPermutation);
  }

  private int[] getSortingPermutation(int sortIndex, int direction, int counterIndex, boolean column) {
    if (direction == 0 || sortIndex < 0)
      return null;
    int rowCount = myRows.size();
    int columnCount = myColumns.size();
    if (column && sortIndex >= columnCount)
      return null;
    if (!column && sortIndex >= rowCount)
      return null;
    int permLength = column ? rowCount : columnCount;
    if (permLength <= 1)
      return null;
    Integer[] p = new Integer[permLength];
    for (int i = 0; i < permLength; i++) {
      p[i] = i;
    }
    PermutationComparator cmp =
      new PermutationComparator(counterIndex, rowCount, columnCount, sortIndex, direction, column);
    // do not touch last element - totals
    Arrays.sort(p, 0, permLength - 1, cmp);
    int[] permutation = new int[permLength];
    for (int i = 0; i < permLength; i++) {
      permutation[i] = p[i];
    }
    return permutation;
  }

  private boolean shouldStop() {
    return myCancelled.get();
  }

  private class Counter implements Runnable {
    public void run() {
      boolean success = false;
      int[] totals = null;
      try {
        if (shouldStop())
          return;

        BitSet2[] rowBits = new BitSet2[myRows.size()];
        BitSet2[] columnBits = new BitSet2[myColumns.size()];
        BitSet2[] counterBits = new BitSet2[myCounters.size()];

        fillBits(rowBits, columnBits, counterBits);

        if (shouldStop())
          return;

        BitSet2 allCounters = union(counterBits);

        if (shouldStop())
          return;

        // warning: myRows and myColumns are changed!
        cleanEmpty(myRows, rowBits, allCounters);
        cleanEmpty(myColumns, columnBits, allCounters);

        if (shouldStop())
          return;

        int rowCount = myRows.size();
        int columnCount = myColumns.size();
        int counterCount = myCounters.size();

        totals = new int[counterCount * rowCount * columnCount];
        fillTotals3(totals, counterBits, counterCount, rowBits, rowCount, columnBits, columnCount);

        if (shouldStop())
          return;
        success = true;
      } finally {
        final boolean finalSuccess = success;
        final int[] finalTotals = totals;
        ThreadGate.AWT.execute(new Runnable() {
          public void run() {
            setCounted(finalTotals, finalSuccess);
          }
        });
      }
    }

    private BitSet2 union(BitSet2[] counterBits) {
      BitSet2 set = new BitSet2();
      for (BitSet2 bits : counterBits) {
        if (bits != null) {
          set.or(bits);
        }
      }
      return set;
    }

    private void fillBits(final BitSet2[] rowBits, final BitSet2[] columnBits, final BitSet2[] counterBits) {
      Database.require().readForeground(new ReadTransaction<Object>() {
        public Object transaction(DBReader reader) {
          final Map<DBAttribute, Object> cache = Collections15.hashMap();
          int k = 0;
          final LongArray items = myFilter.query(reader).copyItemsSorted();
          final int size = items.size();
          for (int i = 0; i < size; i++) {
            if (shouldStop()) {
              return null;
            }
            cache.clear();
            long item = items.get(i);
            fillFilterListBits(item, k, myRows, rowBits, reader, cache, size);
            fillFilterListBits(item, k, myColumns, columnBits, reader, cache, size);
            fillFilterListBits(item, k, myCounters, counterBits, reader, cache, size);
            k++;
          }
          return null;
        }
      }).waitForCompletion();
    }

    private void fillFilterListBits(long item, int revisionIndex, List<STFilter> filters, BitSet2[] bits,
      DBReader reader, Map<DBAttribute, Object> cache, int size)
    {
      int count = filters.size();
      for (int i = 0; i < count; i++) {
        final STFilter filter = filters.get(i);

        final boolean accepted;
        final DBAttribute attr = filter.getAttribute();
        if(attr != null) {
          Object attrValue = cache.get(attr);
          if(attrValue == null) {
            attrValue = reader.getValue(item, attr);
            cache.put(attr, attrValue);
          }
          accepted = filter.accepts(attrValue);
        } else {
          accepted = filter.accepts(item, reader);
        }

        if (accepted) {
          BitSet2 set = bits[i];
          if (set == null) {
            bits[i] = set = new BitSet2(size);
          }
          set.set(revisionIndex);
        }
      }
    }

    private void fillTotals3(int[] totals, BitSet2[] counterBits, int counterCount, BitSet2[] rowBits, int rowCount,
      BitSet2[] columnBits, int columnCount)
    {
      assert counterBits.length == counterCount;
      assert rowBits.length >= rowCount;
      assert columnBits.length >= columnCount;
      assert totals.length == counterCount * rowCount * columnCount;
      int z = 0;
      for (int i = 0; i < counterCount; i++) {
        for (int j = 0; j < rowCount; j++) {
          for (int k = 0; k < columnCount; k++) {
            totals[z++] = BitSet2.intersectionCardinality(counterBits[i], rowBits[j], columnBits[k]);
          }
        }
      }
    }

    private void cleanEmpty(List<STFilter> list, BitSet2[] bits, BitSet2 allCounters) {
      if (bits.length != list.size()) {
        assert false : this;
        return;
      }
      int length = bits.length;
      int removed = 0;
      for (int i = 0; i < length; i++) {
        BitSet2 bitset = bits[i];
        boolean remove = bitset == null || BitSet2.intersectionCardinality(bitset, allCounters) == 0;
        if (remove) {
          if (list.get(i - removed) != STFilter.TOTAL) {
            list.remove(i - removed);
            removed++;
          } else {
            bits[i - removed] = new BitSet2();
          }
        } else {
          assert bitset.cardinality() > 0;
          // pack
          if (removed > 0) {
            bits[i - removed] = bitset;
          }
        }
      }
      assert list.size() == length - removed;
    }
  }


  private class PermutationComparator implements Comparator<Integer> {
    private final int myDirection;

    private final int myOffset;
    private final int myStep;

    public PermutationComparator(int counterIndex, int rowCount, int columnCount, int sorterIndex, int sorterDirection,
      boolean sorterIsColumn)
    {
      myDirection = sorterDirection;
      if (sorterIsColumn) {
        myOffset = counterIndex * rowCount * columnCount + sorterIndex;
        myStep = columnCount;
      } else {
        myOffset = (counterIndex * rowCount + sorterIndex) * columnCount;
        myStep = 1;
      }
    }

    public int compare(Integer i1, Integer i2) {
      int v1 = myTotals[(myOffset + i1 * myStep)];
      int v2 = myTotals[(myOffset + i2 * myStep)];
      if (v1 < v2)
        return myDirection > 0 ? -1 : 1;
      else if (v1 > v2)
        return myDirection > 0 ? 1 : -1;
      else
        return 0;
    }
  }
}
