package com.almworks.util.collections;

import com.almworks.integers.LongArray;
import com.almworks.integers.*;
import com.almworks.util.commons.Condition;
import org.almworks.util.Collections15;
import org.almworks.util.Const;
import org.jetbrains.annotations.*;

import java.util.*;

public class LongSet implements LongList, LongCollector {
  private final LongArray myValues;

  public LongSet() {
    this(0);
  }

  public LongSet(int capacity) {
    myValues = new LongArray(capacity);
  }

  public void add(long value) {
    int index = myValues.binarySearch(value);
    if (index >= 0) return;
    myValues.insert(-index - 1, value);
  }

  public boolean addAllCheckChange(LongList list) {
    int oldSize = myValues.size();
    addAll(list);
    return oldSize < size();
  }

  public void addAll(LongList list) {
    if (list == null || list.isEmpty()) return;
    myValues.addAll(list);
    myValues.sortUnique();
  }

  @Override
  public void addAll(LongIterator iterator) {
    myValues.addAll(iterator);
    myValues.sortUnique();
  }

  public void clear() {
    myValues.clear();
  }

  public long get(int index) throws NoSuchElementException {
    return myValues.get(index);
  }

  public int size() {
    return myValues.size();
  }

  public int indexOf(long value) {
    int index = myValues.binarySearch(value);
    return index < 0 ? -1 : index;
  }

  public long[] toArray(int startIndex, long[] dest, int destOffset, int length) {
    return myValues.toArray(startIndex, dest, destOffset, length);
  }

  @NotNull
  public LongListIterator iterator() {
    return myValues.iterator();
  }

  @NotNull
  public LongListIterator iterator(int from) {
    return myValues.iterator(from);
  }

  @NotNull
  public LongListIterator iterator(int from, int to) {
    return myValues.iterator(from, to);
  }

  public int binarySearch(long value) {
    return myValues.binarySearch(value);
  }

  public int binarySearch(long value, int from, int to) {
    return myValues.binarySearch(value, from, to);
  }

  public boolean contains(long value) {
    return myValues.binarySearch(value) >= 0;
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (o instanceof LongSet) return myValues.equals(((LongSet) o).myValues);
    return myValues.equals(o);
  }

  public int getNextDifferentValueIndex(int curIndex) {
    return myValues.getNextDifferentValueIndex(curIndex);
  }

  public int hashCode() {
    return myValues.hashCode();
  }

  public boolean isEmpty() {
    return myValues.isEmpty();
  }

  public boolean isSorted() {
    return true;
  }

  public boolean isUniqueSorted() {
    return true;
  }

  public LongList subList(int from, int to) {
    return myValues.subList(from, to);
  }

  public List<Long> toList() {
    return myValues.toList();
  }

  public long[] toNativeArray() {
    return myValues.toNativeArray();
  }

  public String toString() {
    return myValues.toString();
  }

  public boolean intersects(LongList list) {
    if (list == null || list.isEmpty()) return false;
    for (int i = 0; i < list.size(); i++) if (contains(list.get(i))) return true;
    return false;
  }

  /**
   * @return items from list except the items contained in this set.
   */
  public LongList filterOutContained(LongList list) {
    LongArray copy = null;
    for (int i = 0; i < list.size(); i++) {
      long value = list.get(i);
      if (contains(value)) {
        if (copy == null) {
          copy = new LongArray();
          copy.addAllNotMore(list.iterator(), i);
        }
      } else if (copy != null) copy.add(value);
    }
    return copy == null ? list : copy;
  }

  /**
   * Removes all requested values
   * @param list values to remove
   * @return true iff set was changed - iff at least one value was removed
   */
  public boolean removeAll(LongList list) {
    if (isEmpty()) return false;
    boolean changed = false;
    for (int i = 0; i < list.size(); i++) {
      long val = list.get(i);
      int index = binarySearch(val);
      if (index < 0) continue;
      int min = index;
      while (min > 0 && myValues.get(min - 1) == val) min--;
      int max = index;
      while (max < size() - 1 && myValues.get(max + 1) == val) max++;
      long replacement;
      if (min > 0) replacement = myValues.get(min - 1);
      else if (max < size() - 1) replacement = myValues.get(max + 1);
      else {
        clear();
        return true;
      }
      changed = true;
      for (int j = min; j <= max; j++) myValues.set(j, replacement);
      assert myValues.isSorted();
    }
    if (changed) myValues.sortUnique();
    return changed;
  }

  /**
   * Removes the value from set
   * @param value value to remove
   * @return true iff the is changed
   */
  public boolean remove(long value) {
    int index = myValues.binarySearch(value);
    if (index >= 0) {
      myValues.removeAt(index);
      return true;
    }
    return false;
  }

  public void addAll(long[] values) {
    if (values == null || values.length == 0) return;
    addAll(LongArray.create(values));
  }

  public boolean containsAll(LongList list) {
    if (list == null || list.isEmpty()) return true;
    for (int i = 0; i < list.size(); i++) if (!contains(list.get(i))) return false;
    return true;
  }

  public Set<Long> toObjectSet() {
    Set<Long> result = Collections15.hashSet(size());
    for (int i = 0; i < size(); i++) result.add(get(i));
    return result;
  }

  public static LongSet copy(LongIterable iterable) {
    LongSet set = new LongSet();
    if (iterable != null) {
      set.myValues.addAll(iterable.iterator());
      set.myValues.sortUnique();
    }
    return set;
  }

  @NotNull
  public static LongList toUniqueSorted(LongList list) {
    if (list == null) list = LongList.EMPTY;
    if (!list.isUniqueSorted()) {
      LongArray copy = new LongArray();
      copy.addAll(list);
      copy.sortUnique();
      list = copy;
    }
    return list;
  }

  public static LongSet setDifference(LongList superSet, LongList subset) {
    subset = toUniqueSorted(subset);
    LongSet complement = new LongSet();
    for (int i = 0; i < superSet.size(); i++) {
      long v = superSet.get(i);
      if (subset.binarySearch(v) < 0) complement.add(v);
    }
    return complement;
  }

  public static <T> LongList collect(Convertor<? super T, Long> convertor, Collection<T> objects) {
    return selectThenCollect(null, convertor, objects);
  }

  public static <T> LongList selectThenCollect(
    @Nullable Condition<? super T> condition, Convertor<? super T, Long> convertor, Collection<T> objects)
  {
    if (objects == null || objects.isEmpty()) {
      return EMPTY;
    }

    final LongArray result = new LongArray(objects.size());
    for(final T t : objects) {
      if(condition == null || condition.isAccepted(t)) {
        final Long v = convertor.convert(t);
        if(v != null) {
          result.add(v);
        }
      }
    }

    result.sortUnique();
    return result;
  }

  public static LongSet create(long ... values) {
    if (values == null) values = Const.EMPTY_LONGS;
    LongSet result = new LongSet(values.length);
    if (values.length > 0) result.addAll(values);
    return result;
  }

  public void retainAll(LongList other) {
    if (other == null || other.isEmpty()) {
      clear();
      return;
    }
    int i = 0;
    while (i < myValues.size()) {
      long value = myValues.get(i);
      if (other.contains(value)) i++;
      else myValues.removeAt(i);
    }
  }
}
