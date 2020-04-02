package com.almworks.util.advmodel;

import com.almworks.util.commons.Condition;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.List;

/**
 * @author dyoma
 */
public class DefaultSubsetModel<T> extends SubsetModel<T> {
  private final OrderListModel<T> mySubset = new OrderListModel<T>();
  private final ImageBasedDecorator<T, T> myComplementSet;

  public DefaultSubsetModel(Lifespan lifespan, AListModel<T> fullSet, final boolean byDefaultInSubset) {
    myComplementSet = new ImageBasedDecorator.ComplemetaryListModel<T>(fullSet, mySubset) {
      public OrderListModel<T> getSubsetModel() {
        return (OrderListModel<T>) super.getSubsetModel();
      }

      public int addNewItems(int sourceIndex, int length, int firstImageIndex) {
        if (!byDefaultInSubset)
          return super.addNewItems(sourceIndex, length, firstImageIndex);
        getSubsetModel().addAll(getSource().subList(sourceIndex, sourceIndex + length));
        return firstImageIndex;
      }
    };

    if (byDefaultInSubset) {
      setFull();
    }
    lifespan.add(myComplementSet.getDetach());
  }

  public void setFull() {
    AListModel<? extends T> fullSet = myComplementSet.getSource();
    mySubset.clear();
    mySubset.addAll(fullSet.toList());
  }

  protected AListModel<T> getImageModel() {
    return mySubset;
  }

  public AListModel<T> getComplementSet() {
    return myComplementSet;
  }

  public AListModel<T> getFullSet() {
    return myComplementSet.getSource();
  }

  public void addFromComplementSet(List<T> items) {
    mySubset.addAll(items);
    myComplementSet.resynch();
  }

  public void insertFromComplementSet(int index, T item) {
    mySubset.insert(index, item);
    myComplementSet.resynch();
  }

  public void swap(int index1, int index2) {
    mySubset.swap(index1, index2);
  }

  public void add(T item) {
    mySubset.addElement(item);
    myComplementSet.resynch();
  }

  public void setSubset(Collection<T> items) {
    assert checkItems(items);
    mySubset.clear();
    mySubset.addAll(items);
    myComplementSet.resynch();
  }

  private boolean checkItems(Collection<T> items) {
    AListModel<T> fullSet = getFullSet();
    for (T item : items) {
      assert fullSet.indexOf(item) >= 0 : item;
    }
    return true;
  }

  public void addFromFullSet(int[] indices) {
    List<? extends T> items = myComplementSet.getSource().getAt(indices);
    mySubset.addAll(items);
    myComplementSet.resynch();
  }

  public void removeAllAt(int[] indices) {
    mySubset.removeAll(indices);
    myComplementSet.resynch();
  }

  @Override
  public void removeAll(List<T> items) {
    if (items == null) return;
    mySubset.removeAll(items);
    myComplementSet.resynch();
  }

  @Override
  public void removeAll(@NotNull Condition<? super T> which) {
    mySubset.removeAll(which);
    myComplementSet.resynch();
  }

  public void setSubsetIndices(int[] originalIndices) {
    AListModel<T> fullSet = getFullSet();
    if (mySubset.getSize() == originalIndices.length) {
      boolean changed = false;
      for (int i = 0; i < originalIndices.length; i++) {
        int index = originalIndices[i];
        if (mySubset.getAt(i) == fullSet.getAt(index)) continue;
        changed = true;
        break;
      }
      if (!changed) return;
    }
    mySubset.clear();
    mySubset.addAll(fullSet.getAt(originalIndices));
    myComplementSet.resynch();
  }
}
