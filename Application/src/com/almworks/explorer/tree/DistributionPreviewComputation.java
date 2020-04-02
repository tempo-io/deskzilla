package com.almworks.explorer.tree;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ResolvedItem;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.application.tree.GenericNode;
import com.almworks.api.application.tree.ItemsPreview;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.integers.IntArray;
import com.almworks.integers.LongArray;
import com.almworks.items.api.*;
import com.almworks.util.Pair;
import com.almworks.util.collections.LongSet;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.Threads;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifespan;

import java.util.Collection;
import java.util.List;

public class DistributionPreviewComputation {
  private final BaseEnumConstraintDescriptor myDescriptor;
  private final DBFilter myFilter;
  private final List<DistributionQueryNodeImpl> myChildren;
  private final List<Object> myResolved = Collections15.arrayList();
  private final LongSet myAllValues = new LongSet();
  private final IntArray myCount = new IntArray();

  public DistributionPreviewComputation(BaseEnumConstraintDescriptor descriptor, DBFilter filter,
    List<DistributionQueryNodeImpl> childQueries) {
    myDescriptor = descriptor;
    myFilter = filter;
    myChildren = childQueries;
  }

  public void perform(Lifespan lifespan, DBReader reader, ItemHypercube cube) {
    if (lifespan.isEnded()) return;
    collectQueriedItems(cube);
    collectItems();
    if (myAllValues.isEmpty()) {
      for (int i = 0, myChildrenSize = myChildren.size(); i < myChildrenSize; i++) {
        DistributionQueryNodeImpl child = myChildren.get(i);
        child.setPreview(new ItemsPreview.Unavailable());
      }
      return;
    }
    long start = System.currentTimeMillis();
    LongArray items = myFilter.query(reader).copyItemsSorted();
    long duration = System.currentTimeMillis() - start;
    Log.debug("Distribution count: " + duration + "ms/" + items.size() + "count " + myChildren.size() + "childCount " + myFilter.getExpr());
    DBAttribute attribute = myDescriptor.getAttribute();
    if (!Long.class.equals(attribute.getScalarClass())) {
      Log.error("Wrong attribute " + attribute);
      setNoPreview();
      return;
    }
    switch (attribute.getComposition()) {
    case SCALAR: readScalarValues(reader, attribute, items); break;
    case SET:
    case LIST: readCollectionValues(reader, attribute, items); break;
    default:
      Log.error("Wrong attribute " + attribute);
      setNoPreview();
      return;
    }
    ThreadGate.AWT.execute(new SetPreviewRunnable());
  }

  private int getCount(ResolvedItem value) {
    int index = myAllValues.indexOf(value.getResolvedItem());
    return myCount.get(index);
  }

  private void readCollectionValues(DBReader reader, DBAttribute<? extends Collection<Long>> attribute, LongArray items) {
    for (int i = 0; i < items.size(); i++) {
      long item = items.get(i);
      Collection<Long> values = attribute.getValue(item, reader);
      if (values == null) continue;
      for (Long value : values) incValueCount(value);
    }
  }

  private void readScalarValues(DBReader reader, DBAttribute<Long> attribute, LongArray items) {
    for (int i = 0; i < items.size(); i++) {
      long item = items.get(i);
      incValueCount(attribute.getValue(item, reader));
    }
  }

  private void incValueCount(Long value) {
    if (value == null || value <= 0) return;
    int index = myAllValues.indexOf(value);
    if (index >= 0) myCount.set(index, myCount.get(index) + 1);
  }

  private void setNoPreview() {
    Log.warn("Setting no preview " + myDescriptor + " " + myChildren.size());
    for (DistributionQueryNodeImpl child : myChildren) child.setPreview(new ItemsPreview.Unavailable());
  }

  private void collectItems() {
    for (Object itemOrList : myResolved) {
      if (itemOrList == null) continue;
      if (itemOrList instanceof List) {
        List<ResolvedItem> items = (List<ResolvedItem>) itemOrList;
        for (ResolvedItem item : items) myAllValues.add(item.getResolvedItem());
      } else if (itemOrList instanceof ResolvedItem) {
        myAllValues.add(((ResolvedItem) itemOrList).getResolvedItem());
      } else Log.error("Unknown object " + itemOrList);
    }
    myCount.insertMultiple(0, 0, myAllValues.size());
  }

  private void collectQueriedItems(ItemHypercube cube) {
    for (DistributionQueryNodeImpl child : myChildren) {
      ItemsPreview preview = child.getPreview(false);
      Object resolvedItems = null;
      if (preview == null || !preview.isValid()) {
        Pair<ConstraintDescriptor, ItemKey> pair = child.getAttributeValue();
        if (pair == null || !myDescriptor.equals(pair.getFirst())) {
          if (pair != null) Log.error("Unexpected descriptor " + pair.getFirst() + " " + myDescriptor);
          child.setPreview(new ItemsPreview.Unavailable());
        } else {
          resolvedItems = child.getResolvedItems(cube);
          if (resolvedItems == null || ((resolvedItems instanceof List) && (((List) resolvedItems).isEmpty()))) {
            child.setPreview(new CountPreview(0));
          }
        }
      }
      if ((resolvedItems instanceof List) && (((List) resolvedItems).isEmpty())) resolvedItems = null;
      myResolved.add(resolvedItems);
    }
    assert myChildren.size() == myResolved.size();
  }

  private class SetPreviewRunnable implements Runnable {
    private GenericNode myCurrentParent = null;
    private int myLastIndex = 0;
    private int myParentChildCount = 0;
    private final IntArray myChangedIndexes = new IntArray();

    @Override
    public void run() {
      Threads.assertAWTThread();
      for (int i = 0, myChildrenSize = myChildren.size(); i < myChildrenSize; i++) {
        DistributionQueryNodeImpl child = myChildren.get(i);
        Object resolved = myResolved.get(i);
        setCurrentParent(child.getParent());
        if (resolved != null) {
          int count;
          if (resolved instanceof List) {
            count = 0;
            for (ResolvedItem value : (List<ResolvedItem>) resolved) {
              count += getCount(value);
            }
          } else if (resolved instanceof ResolvedItem) {
            count = getCount((ResolvedItem) resolved);
          } else
            count = 0;
          child.setPreviewSilent(new CountPreview(count));
          markChildChanged(child);
        }
        myLastIndex++;
      }
      setCurrentParent(null);
    }

    private void markChildChanged(DistributionQueryNodeImpl child) {
      GenericNode parent = child.getParent();
      if (parent == null) return;
      if (parent != myCurrentParent) {
        child.fireTreeNodeChanged();
        return;
      }
      int index;
      if (myLastIndex < myParentChildCount) {
        GenericNode expected = parent.getChildAt(myLastIndex);
        index = expected == child ? myLastIndex : -1;
      } else index = -1;
      if (index < 0) {
        index = parent.getTreeNode().getIndex(child.getTreeNode());
        if (index >= 0) myLastIndex = index;
      }
      myChangedIndexes.add(index);
    }

    private void setCurrentParent(GenericNode parent) {
      if (myCurrentParent == parent) return;
      if (myCurrentParent != null) {
        myCurrentParent.getTreeNode().fireChildrenChanged(myChangedIndexes);
        myCurrentParent.fireTreeNodeChanged();
      }
      myChangedIndexes.clear();
      myCurrentParent = parent;
      myLastIndex = 0;
      myParentChildCount = parent != null ? parent.getChildrenCount() : 0;
    }
  }
}
