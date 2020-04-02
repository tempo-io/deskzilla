package com.almworks.api.application;

import com.almworks.api.application.util.ModelKeyUtils;
import com.almworks.api.engine.Connection;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.Pair;
import com.almworks.util.models.ColumnSizePolicy;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Collection;

// todo #854 extract interface
/**
 * @author : Dyoma
 */
public abstract class AttributeModelKey<T, S> implements ModelKey<T>, UserModifiable {
  private final TypedKey<T> myValueKey;
  private final DBAttribute<S> myAttribute;
  private final String myDisplayableName;
  private final TypedKey<Boolean> myModifiedKey;

  protected AttributeModelKey(DBAttribute<S> attribute, String displayableName) {
    this(attribute, displayableName, attribute.getName());
  }

  protected AttributeModelKey(DBAttribute<S> attribute, String displayableName, String keyId) {
    myAttribute = attribute;
    myDisplayableName = displayableName;
    myValueKey = TypedKey.create(keyId);
    myModifiedKey = TypedKey.create(keyId + ".userModified");
  }

  public void setValue(PropertyMap values, T value) {
    values.put(myValueKey, value);
  }

  public T getValue(PropertyMap values) {
    return values.get(myValueKey);
  }

  public boolean hasValue(PropertyMap values) {
    return values.containsKey(myValueKey);
  }

  public DBAttribute<S> getAttribute() {
    return myAttribute;
  }

  @NotNull
  public final String getName() {
    return myValueKey.getName();
  }

  protected TypedKey<T> getValueKey() {
    return myValueKey;
  }

  public String getDisplayableName() {
    return myDisplayableName;
  }

  public boolean isSystem() {
    return false;
  }

  public ColumnSizePolicy getRendererSizePolicy() {
    return ColumnSizePolicy.FREE;
  }

  public Pair<String, ExportValueType> formatForExport(PropertyMap values, NumberFormat numberFormat, DateFormat dateFormat,
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
    return myDisplayableName;
  }

  public DataPromotionPolicy getDataPromotionPolicy() {
    return DataPromotionPolicy.STANDARD;
  }

  public boolean isUserModified(ModelMap modelMap) {
    final Boolean modified = modelMap.get(myModifiedKey);
    return modified != null && modified.booleanValue();
  }

  public void setUserModified(ModelMap modelMap, boolean modified) {
    modelMap.put(myModifiedKey, modified);
  }
}
