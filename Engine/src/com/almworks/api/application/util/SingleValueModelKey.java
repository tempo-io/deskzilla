package com.almworks.api.application.util;

import com.almworks.api.application.*;
import com.almworks.api.engine.Connection;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.Pair;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.models.ColumnSizePolicy;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.*;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Collection;

/**
 * @author : Dyoma
 */
public abstract class SingleValueModelKey<M, V> implements ModelKey<V> {
  private final DBAttribute<V> myAttribute;
  private final TypedKey<M> myKey;
  private final TypedKey<V> myValueKey;
  private final String myName;
  private final String myDisplayableName;
  private CanvasRenderer<PropertyMap> myCanvasRenderer;
  private final boolean myExportAsString;
  private final boolean myMultilineExport;
  private ModelMergePolicy myMergePolicy = ModelMergePolicy.MANUAL;

  protected SingleValueModelKey(DBAttribute<V> attribute, boolean exportAsString, boolean multilineExport, String displayableName) {
    this(attribute, exportAsString, multilineExport, displayableName, null);
  }

  protected SingleValueModelKey(DBAttribute<V> attribute, boolean exportAsString, boolean isMultilineExport, String displayableName, CanvasRenderer<PropertyMap> renderer) {
    myExportAsString = exportAsString;
    myMultilineExport = isMultilineExport;
    myAttribute = attribute;
    myName = myAttribute.getName();
    myKey = TypedKey.create(myName);
    myValueKey = TypedKey.create(myName + "#value");
    myCanvasRenderer = renderer != null ? renderer : createToStringRenderer(myValueKey);
    myDisplayableName = displayableName;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  protected M findSingleValue(ModelMap model) {
    return model.get(myKey);
  }

  protected void setupSingleValue(ModelMap model, M value) {
    model.put(myKey, value);
  }

  protected void register(ModelMap modelMap) {
    if (myName != null)
      modelMap.registerKey(myName, this);
  }

  public <SM> SM getModel(Lifespan lifespan, ModelMap model, Class<SM> aClass) {
    assert aClass != null;
    assert model != null;
    M m = findSingleValue(model);
    assert m != null : getName();
    if (aClass.isInstance(m))
      return (SM) m;
    return convertModel(m, aClass);
  }

  protected <SM> SM convertModel(M model, Class<? extends SM> aClass) {
    assert false : "Subclass responsibility. this=" + toString() + " model=" + " expected class=" + aClass;
    throw new Failure("Subclass responsibility. this=" + toString() + " model=" + " expected class=" + aClass);
  }

  public boolean hasValue(ModelMap model) {
    return model.get(myKey) != null;
  }

  public void setValue(PropertyMap values, V value) {
    values.put(myValueKey, value);
  }

  public V getValue(PropertyMap values) {
    return values.get(myValueKey);
  }

  public boolean isEqualValue(ModelMap models, PropertyMap values) {
    return Util.equals(getValue(models), getValue(values));
  }

  public boolean isEqualValue(PropertyMap values1, PropertyMap values2) {
    return Util.equals(getValue(values1), getValue(values2));
  }

  public boolean hasValue(PropertyMap values) {
    return values.containsKey(myValueKey);
  }

  public void addChanges(UserChanges changes) {
    V userInput = changes.getNewValue(this);
    changes.getCreator().setValue(myAttribute, userInput);
  }

  public DBAttribute<V> getAttribute() {
    return myAttribute;
  }

  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    setValue(values, itemVersion.getValue(myAttribute));
  }

  public String getDisplayableName() {
    return myDisplayableName;
  }

  public String toString() {
    return myName;
  }

  public boolean isSystem() {
    return false;
  }

  @NotNull
  public CanvasRenderer<PropertyMap> getRenderer() {
    return myCanvasRenderer;
  }

  protected void setRenderer(CanvasRenderer<PropertyMap> renderer) {
    myCanvasRenderer = renderer;
  }

  public void takeSnapshot(PropertyMap to, ModelMap from) {
    setValue(to, getValue(from));
  }

  @NotNull
  public static <V> CanvasRenderer<PropertyMap> createToStringRenderer(final TypedKey<V> valueKey) {
    return new CanvasRenderer<PropertyMap>() {
      public void renderStateOn(CellState state, Canvas canvas, PropertyMap item) {
        V value = item.get(valueKey);
        if (value != null) {
          canvas.appendText(String.valueOf(value));
        }
      }
    };
  }

  public ModelMergePolicy getMergePolicy() {
    return myMergePolicy;
  }

  public void setMergePolicy(@NotNull ModelMergePolicy mergePolicy) {
    myMergePolicy = mergePolicy;
  }

  public ColumnSizePolicy getRendererSizePolicy() {
    return ColumnSizePolicy.FREE;
  }

  @Nullable
  public Pair<String, ExportValueType> formatForExport(PropertyMap values, NumberFormat numberFormat,
    DateFormat dateFormat, boolean htmlAccepted)
  {
    if (myExportAsString) {
      final ExportValueType type;
      final String string;
      V value = getValue(values);
      if (value == null) {        
        string = "";
      } else {
        string = value.toString();
      }
      type = myMultilineExport ? ExportValueType.LARGE_STRING : ExportValueType.STRING ;
      return Pair.create(string, type);
    } else {
      return ModelKeyUtils.getFormattedString(this, values, numberFormat, dateFormat, myMultilineExport);
    }
  }

  public boolean isExportable(Collection<Connection> connections) {
    return true;
  }

  public <T>ModelOperation<T> getOperation(TypedKey<T> key) {
    return null;
  }

  public DataPromotionPolicy getDataPromotionPolicy() {
    return DataPromotionPolicy.STANDARD;
  }

  public static class SetValueOperation<M, V> implements ModelOperation<V> {
    private final SingleValueModelKey<M, V> myKey;

    public SetValueOperation(SingleValueModelKey<M, V> key) {
      myKey = key;
    }

    @Override
    public void perform(ItemUiModel model, V argument) throws CantPerformExceptionExplained {
      PropertyMap map = new PropertyMap();
      myKey.setValue(map, argument);
      myKey.copyValue(model.getModelMap(), map);
    }

    @Nullable
    @Override
    public String getArgumentProblem(V argument) {
      return null;
    }
  }

}