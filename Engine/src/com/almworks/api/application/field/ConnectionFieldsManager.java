package com.almworks.api.application.field;

import com.almworks.api.application.LoadedItem;
import com.almworks.api.application.order.Order;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.integers.LongIterator;
import com.almworks.items.api.*;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.AListModelUpdater;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.models.TableColumnAccessor;
import com.almworks.util.threads.CanBlock;
import com.almworks.util.threads.Threads;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifecycle;
import org.jetbrains.annotations.*;
import util.concurrent.SynchronizedBoolean;

import java.util.*;

import static org.almworks.util.Collections15.hashMap;

public abstract class ConnectionFieldsManager<T extends ItemField> {
  private final Map<Long, T> myFields = hashMap();
  private final Map<DBAttribute, Order> myOrdersByAttribute = Collections15.hashMap();
  private final AListModelUpdater<Order> myOrders = AListModelUpdater.create(50);
  private final AListModelUpdater<TableColumnAccessor<LoadedItem, ?>> myCustomColumns = AListModelUpdater.create(50);
  private final AListModelUpdater<ConstraintDescriptor> myDescriptors = AListModelUpdater.create(50);

  private final Lifecycle myLife = new Lifecycle();
  private final SynchronizedBoolean myInitialLoadFinished = new SynchronizedBoolean(false);
  private final BoolExpr<DP> myFieldsFilter;

  public ConnectionFieldsManager(BoolExpr<DP> fieldsFilter) {
    myFieldsFilter = fieldsFilter;
  }

  /**
   * Waits for the fields to be loaded from the db.
   */
  @CanBlock
  public void start(Database db) throws InterruptedException {
    Threads.assertLongOperationsAllowed();
    db.liveQuery(myLife.lifespan(), myFieldsFilter, new FieldsLoader());
    myInitialLoadFinished.waitForValue(true);
  }

  public void stop() {
    myLife.dispose();
  }

  private boolean checkInitialLoadFinished() {
    if (!myInitialLoadFinished.get()) {
      assert false;
      Log.warn("CFM: initial load not finished", new Throwable());
      return false;
    }
    return true;
  }

  public Set<T> copyExisting() {
    return copyExisting(Collections15.<T>linkedHashSet());
  }

  public List<T> copyAndSortExisting(Comparator<T> comparator) {
    List<T> list = copyExisting(Collections15.<T>arrayList());
    Collections.sort(list, comparator);
    return list;
  }

  public <C extends Collection<T>> C copyExisting(C to) {
    if (checkInitialLoadFinished()) {
      synchronized (myFields) {
        to.addAll(myFields.values());
      }
    }
    return to;
  }

  @Nullable
  public T getByAttributeId(String id) {
    if (id == null || !checkInitialLoadFinished()) return null;
    synchronized (myFields) {
      for (T field : myFields.values()) {
        if (id.equals(field.getAttribute().getId())) {
          return field;
        }
      }
    }
    return null;
  }

  @Nullable
  public ItemField getByAttribute(DBAttribute<?> attr) {
    return attr == null ? null : getByAttributeId(attr.getId());
  }

  public AListModel<TableColumnAccessor<LoadedItem, ?>> getColumns() {
    return myCustomColumns.getModel();
  }

  public AListModel<ConstraintDescriptor> getDescriptors() {
    return myDescriptors.getModel();
  }

  public AListModel<? extends Order> getOrders() {
    return myOrders.getModel();
  }

  @Nullable
  protected abstract T createNonStartedOrUpdateField(DBReader reader, long field, @Nullable T prevField);

  private void registerField(T field, DBReader reader) {
    if (field == null) return;

    field.start(reader);
    TableColumnAccessor<LoadedItem, ?> column = field.getColumn();
    if (column != null) {
      myCustomColumns.add(column);
    }
    ConstraintDescriptor descriptor = field.getDescriptor();
    if (descriptor != null) {
      myDescriptors.add(descriptor);
    }
    Order order = field.createOrder();
    if (order != null) {
      synchronized (myOrdersByAttribute) {
        Order prev = myOrdersByAttribute.put(field.getAttribute(), order);
        if (prev != null) {
          Log.warn("CFM.rF: " + prev + ' ' + field);
        }
      }
      myOrders.add(order);
    }
  }

  private void unregisterField(T field) {
    if (field == null) return;
    DBAttribute attr = field.getAttribute();
    Order order;
    synchronized (myOrdersByAttribute) {
      order = myOrdersByAttribute.remove(attr);
    }
    if (order != null) {
      myOrders.remove(order);
    }
    TableColumnAccessor<LoadedItem, ?> column = field.getColumn();
    if (column != null)
      myCustomColumns.remove(column);
    ConstraintDescriptor descriptor = field.getDescriptor();
    if (descriptor != null)
      myDescriptors.remove(descriptor);
    field.stop();
  }

  protected void removeField(long item) {
    T field;
    synchronized (myFields) {
      field = myFields.remove(item);
    }
    if (field != null) {
      unregisterField(field);
    }
  }

  private void changeField(long item, DBReader reader) {
    T newField;
    T oldField;
    while (true) {
      T currentField;
      synchronized (myFields) {
        currentField = myFields.get(item);
      }
      newField = createNonStartedOrUpdateField(reader, item, currentField);
      if (newField == currentField) return;
      if (newField == null) {
        removeField(item);
        return;
      }

      synchronized (myFields) {
        oldField = myFields.get(item);
        if (oldField != currentField) continue;
        myFields.put(item, newField);
        break;
      }
    }
    if (oldField != null) unregisterField(oldField);
    registerField(newField, reader);
  }

  private class FieldsLoader implements DBLiveQuery.Listener {
    @Override
    public void onICNPassed(long icn) {
    }

    @Override
    public void onDatabaseChanged(DBEvent event, DBReader reader) {
      for (LongIterator i = event.getAddedAndChangedSorted().iterator(); i.hasNext();) {
        changeField(i.next(), reader);
      }
      for (LongIterator i = event.getRemovedSorted().iterator(); i.hasNext();) {
        removeField(i.next());
      }
      myInitialLoadFinished.set(true);
    }
  }
}
