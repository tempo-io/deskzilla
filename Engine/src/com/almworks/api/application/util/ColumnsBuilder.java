package com.almworks.api.application.util;

import com.almworks.api.application.*;
import com.almworks.util.collections.Containers;
import com.almworks.util.models.TableColumnAccessor;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author dyoma
 */
public class ColumnsBuilder {
  private static final int DEFAULT_WIDTH = 10;
  private final Comparator<LoadedItem> myDefaultOrder;
  @Nullable
  private final ModelKey<Boolean> myDummyFlag;
  private final List<TableColumnAccessor<LoadedItem, ?>> myColumns = Collections15.arrayList();

  public ColumnsBuilder(Comparator<LoadedItem> defaultOrder, @Nullable ModelKey<Boolean> dummyFlag) {
    myDefaultOrder = defaultOrder;
    myDummyFlag = dummyFlag;
  }

  public void addItemColumn(ModelKey<? extends ItemKey> key, int charCount) {
    myColumns.add(ValueColumn.createComparable(key, myDefaultOrder, charCount, myDummyFlag, key.getDisplayableName()));
  }

  public void addItemColumn(ModelKey<? extends ItemKey> key) {
    addItemColumn(key, DEFAULT_WIDTH);
  }

  public void addCollectionColumn(ModelKey<? extends Collection> key, int charCount) {
    addCollectionColumn(key, charCount, key.getDisplayableName());
  }

  public void addCollectionColumn(ModelKey<? extends Collection> key, int charCount, String headerText) {
    Comparator<Collection> valueComparator = new Comparator<Collection>() {
      public int compare(Collection o1, Collection o2) {
        return Containers.compareInts(o1.size(), o2.size());
      }
    };
    myColumns.add(new ValueColumn<Collection>(key, myDefaultOrder, valueComparator, charCount, myDummyFlag, headerText));
  }

  public <S> void addComparableColumn(ModelKey<? extends S> key, final Comparator<S> nonNullComparator, int charCount) {
    myColumns.add(new ValueColumn<S>(key, myDefaultOrder, Containers.nullableComparator(nonNullComparator, true), charCount, myDummyFlag, key.getDisplayableName()));
  }

  public <T extends Comparable> void addValueColumn(ModelKey<T> key, int charCount) {
    addValueColumnImpl(key, charCount, myDummyFlag);
  }

  public <T extends Comparable> void addValueColumnNoDummy(ModelKey<T> key, int charCount) {
    addValueColumnImpl(key, charCount, null);
  }

  public List<TableColumnAccessor<LoadedItem, ?>> getColumns() {
    return myColumns;
  }

  private <T extends Comparable> void addValueColumnImpl(ModelKey<T> key, int charCount, @Nullable ModelKey<Boolean> dummyFlag) {
    myColumns.add(ValueColumn.createComparable(key, myDefaultOrder, charCount, dummyFlag, key.getDisplayableName()));
  }

  public static <T extends Comparable<T>> ColumnsBuilder create(final ModelKey<T> defaultOrderByKey, @Nullable ModelKey<Boolean> isDummy) {
    Comparator<LoadedItem> defaultComparator = new LoadedItemComparator<T>(defaultOrderByKey);

    return new ColumnsBuilder(defaultComparator, isDummy);
  }
}
