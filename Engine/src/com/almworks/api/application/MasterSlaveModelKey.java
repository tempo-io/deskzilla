package com.almworks.api.application;

import com.almworks.api.engine.Connection;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.models.ColumnSizePolicy;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.*;
import org.almworks.util.detach.*;
import org.jetbrains.annotations.*;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.*;

// todo: a lot of duplication with OrderListModelKey
public abstract class MasterSlaveModelKey<T extends Comparable<? super T>> implements ModelKey<List<T>> {
  private final DBAttribute<Long> myMasterAttribute;
  private final String myName;
  private final String myDisplayName;

  private final TypedKey<List<T>> myValueKey;
  private final TypedKey<OrderListModel<T>> myModelKey;

  private CanvasRenderer<PropertyMap> myCanvasRenderer;

  public MasterSlaveModelKey(DBAttribute<Long> masterAttribute, String name, String displayName) {
    myMasterAttribute = masterAttribute;
    myName = name;
    myDisplayName = displayName;
    myValueKey = TypedKey.create(myName);
    myModelKey = TypedKey.create(myName + "#model");
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String getDisplayableName() {
    return myDisplayName;
  }

  @Override
  public boolean hasValue(ModelMap model) {
    return model.get(myValueKey) != null;
  }

  @Override
  public List<T> getValue(ModelMap model) {
    return model.get(myValueKey);
  }

  @Override
  public boolean hasValue(PropertyMap values) {
    return values.containsKey(myValueKey);
  }

  @Override
  public List<T> getValue(PropertyMap values) {
    return values.get(myValueKey);
  }

  @Override
  public void setValue(PropertyMap values, List<T> value) {
    values.put(myValueKey, value);
  }

  @Override
  public <SM> SM getModel(Lifespan lifespan, ModelMap modelMap, Class<SM> aClass) {
    final OrderListModel<T> model = modelMap.get(myModelKey);
    if (aClass.isInstance(model)) {
      lifespan.add(notifyModelMap(modelMap));
      return (SM) model;
    }
    assert false : aClass;
    return null;
  }

  private Detach notifyModelMap(final ModelMap models) {
    DetachComposite life = new DetachComposite();
    models.get(myModelKey).addChangeListener(life, new ChangeListener() {
      @Override
      public void onChange() {
        models.valueChanged(MasterSlaveModelKey.this);
      }
    });
    return life;
  }

  @Override
  public boolean isEqualValue(ModelMap models, PropertyMap values) {
    return Util.equals(getValue(models), getValue(values));
  }

  @Override
  public boolean isEqualValue(PropertyMap values1, PropertyMap values2) {
    return Util.equals(getValue(values1), getValue(values2));
  }

  @Override
  public void takeSnapshot(PropertyMap to, ModelMap from) {
    setValue(to, getValue(from));
  }

  @Override
  public void copyValue(ModelMap to, PropertyMap from) {
    if(to == null || from == null) {
      assert false : this;
      return;
    }

    OrderListModel<T> m = to.get(myModelKey);
    if(m == null) {
      m = OrderListModel.create();
      to.put(myModelKey, m);
      to.registerKey(getName(), this);
    } else if(isEqualValue(to, from)) {
      return;
    }

    final List<T> value = getValue(from);
    m.replaceElementsSet(value);
    to.put(myValueKey, value);
    to.valueChanged(this);
  }

  @Override
  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    final LongList slaves = itemVersion.getSlaves(myMasterAttribute);
    final List<T> extracted = extractValues(slaves, itemVersion, itemServices, values);
    Collections.sort(extracted);
    setValue(values, extracted);
  }

  protected abstract List<T> extractValues(
    LongList slaves, ItemVersion master, LoadedItemServices services, PropertyMap values);

  @Override
  public boolean isSystem() {
    return false;
  }

  @Override
  @NotNull
  public CanvasRenderer<PropertyMap> getRenderer() {
    if(myCanvasRenderer == null) {
      myCanvasRenderer = createRenderer();
    }
    return myCanvasRenderer;
  }

  protected abstract CanvasRenderer<PropertyMap> createRenderer();

  @Override
  public ColumnSizePolicy getRendererSizePolicy() {
    return ColumnSizePolicy.FREE;
  }

  @Override
  public boolean isExportable(Collection<Connection> connections) {
    return false;
  }

  @Override
  public Pair<String, ExportValueType> formatForExport(
    PropertyMap values, NumberFormat numberFormat, DateFormat dateFormat, boolean htmlAccepted)
  {
    return null;
  }

  @Override
  public <T> ModelOperation<T> getOperation(@NotNull TypedKey<T> key) {
    return null;
  }

  @Override
  public DataPromotionPolicy getDataPromotionPolicy() {
    return DataPromotionPolicy.ALWAYS;
  }

  @Override
  public int compare(List<T> o1, List<T> o2) {
    return 0;
  }

//  public void replaceValues(ModelMap modelMap, List<T> keys) {
//    Set<T> pending = Collections15.linkedHashSet(keys);
//    Change change = change(modelMap);
//    for (Iterator<T> ii = change.newValue().iterator(); ii.hasNext();) {
//      T key = ii.next();
//      if (!pending.remove(key)) {
//        ii.remove();
//      }
//    }
//    change.newValue().addAll(pending);
//    change.done();
//  }
//
//  public void addValue(ModelMap modelMap, T value) {
//    Change change = change(modelMap);
//    List<T> list = change.newValue();
//    if (list.indexOf(value) < 0)
//      list.add(value);
//    change.done();
//  }
//
//  public void addValues(ModelMap modelMap, Collection<? extends T> values) {
//    Change change = change(modelMap);
//    List<T> list = change.newValue();
//    for (T v : values)
//      if (list.indexOf(v) < 0)
//        list.add(v);
//    change.done();
//  }

  public void removeValue(ModelMap modelMap, T key) {
    Change change = change(modelMap);
    change.newValue().remove(key);
    change.done();
  }

  protected Change change(ModelMap map) {
    return new Change(map);
  }

  protected class Change {
    private final PropertyMap myProps;
    private final List<T> myNewValue;
    private final ModelMap myMap;

    public Change(ModelMap map) {
      myMap = map;
      myProps = new PropertyMap();
      takeSnapshot(myProps, map);
      Collection<T> value = getValue(myProps);
      myNewValue = Collections15.arrayList(value);
    }

    public List<T> newValue() {
      return myNewValue;
    }

    public void done() {
      setValue(myProps, myNewValue);
      copyValue(myMap, myProps);
    }
  }
}
