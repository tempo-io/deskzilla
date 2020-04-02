package com.almworks.api.application;

import com.almworks.api.application.util.ModelKeyUtils;
import com.almworks.api.engine.Connection;
import com.almworks.util.Pair;
import com.almworks.util.models.ColumnSizePolicy;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.TypedKey;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Collection;

/**
 * @author : Dyoma
 */
public abstract class AbstractModelKey<T> implements ModelKey<T> {
  private final TypedKey<T> myKey;

  protected AbstractModelKey(String name) {
    myKey = TypedKey.create(name);
  }

  @NotNull public String getName() {
    return getModelKey().getName();
  }

  public T getValue(ModelMap model) {
    return model.get(getModelKey());
  }

  @NotNull protected final TypedKey<T> getModelKey() {
    return myKey;
  }

  public boolean hasValue(ModelMap model) {
    return model.get(getModelKey()) != null;
  }

  public void setValue(PropertyMap values, T value) {
    values.put(getModelKey(), value);
  }

  public T getValue(PropertyMap values) {
    return values.get(getModelKey());
  }

  public boolean isEqualValue(ModelMap models, PropertyMap values) {
    return Util.equals(getValue(models), getValue(values));
  }

  public boolean isEqualValue(PropertyMap values1, PropertyMap values2) {
    return Util.equals(getValue(values1), getValue(values2));
  }

  public void copyValue(ModelMap to, PropertyMap from) {
    to.put(getModelKey(), getValue(from));
  }

  public void takeSnapshot(PropertyMap to, ModelMap from) {
    setValue(to, getValue(from));
  }

  public ColumnSizePolicy getRendererSizePolicy() {
    return ColumnSizePolicy.FREE;
  }

  public boolean hasValue(PropertyMap values) {
    return values.containsKey(getModelKey());
  }

  public String getDisplayableName() {
    return getName();
  }

  public int compare(T o1, T o2) {
    return 0;
  }

  public Pair<String,ExportValueType> formatForExport(PropertyMap values, NumberFormat numberFormat, DateFormat dateFormat,
    boolean htmlAccepted) {
    return ModelKeyUtils.getRenderedString(this, values);
  }

  public boolean isExportable(Collection<Connection> connections) {
    return true;
  }

  public <T>ModelOperation<T> getOperation(TypedKey<T> key) {
    return null;
  }

  public String toString() {
    return myKey.getName();
  }

  public DataPromotionPolicy getDataPromotionPolicy() {
    return DataPromotionPolicy.STANDARD;
  }
}
