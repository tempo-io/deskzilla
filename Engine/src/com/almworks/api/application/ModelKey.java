package com.almworks.api.application;

import com.almworks.api.application.util.PredefinedKey;
import com.almworks.api.engine.Connection;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.Pair;
import com.almworks.util.collections.Convertor;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.models.ColumnSizePolicy;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.threads.CanBlock;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;
import util.external.BitSet2;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * @author : Dyoma
 */
public interface ModelKey<V> extends Comparator<V> {
  Convertor<ModelKey<?>, String> GET_NAME = new GetNameConvertor();
  Convertor<ModelKey<?>, String> GET_DISPLAY_NAME = new GetDisplayNameConvertor();
  /**
   * Stores bit set of used model keys
   * IMPORTANT: Do not put bitsets into model manually, use ModelKeySetUtil
   */
  ModelKey<BitSet2> ALL_KEYS = PredefinedKey.create("#allKeys");

  //  ModelKey<Set<ModelKey<?>>> ALL_KEYS = PredefinedKey.create("#allKeys");
  ModelKey<Set<ModelKey<?>>> CHANGED_KEYS = PredefinedKey.create("#changedKeys");
  Condition<ModelKey<?>> USER_KEYS = new UserKeysCondition();
  Comparator<ModelKey<?>> DISPLAY_NAME_COMPARATOR = new DisplayNameComparator();
  CanvasRenderer<ModelKey<?>> DISPLAY_NAME_RENDERER = new CanvasRenderer<ModelKey<?>>() {
    public void renderStateOn(CellState state, Canvas canvas, ModelKey<?> item) {
      canvas.appendText(item != null ? item.getDisplayableName() : "");
    }
  };

  @NotNull
  String getName();

  V getValue(ModelMap model);

  @Nullable
  <SM> SM getModel(Lifespan lifespan, ModelMap model, Class<SM> aClass);

  boolean hasValue(ModelMap model);

  void setValue(PropertyMap values, V value);

  V getValue(PropertyMap values);

  boolean isEqualValue(ModelMap models, PropertyMap values);

  boolean isEqualValue(PropertyMap values1, PropertyMap values2);

  void copyValue(ModelMap to, PropertyMap from);

  boolean hasValue(PropertyMap values);

  @CanBlock
  void addChanges(UserChanges changes);

  void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values);

  String getDisplayableName();

  /**
   * @deprecated
   */
  boolean isSystem();

  // todo return CanvasRenderer<V> ?
  @NotNull
  CanvasRenderer<PropertyMap> getRenderer();

  void takeSnapshot(PropertyMap to, ModelMap from);

  ModelMergePolicy getMergePolicy();

  @Deprecated
  ColumnSizePolicy getRendererSizePolicy();

  @Nullable
  Pair<String, ExportValueType> formatForExport(PropertyMap values, NumberFormat numberFormat, DateFormat dateFormat,
    boolean htmlAccepted);

  boolean isExportable(Collection<Connection> connections);

  @Nullable
  public <T> ModelOperation<T> getOperation(@NotNull TypedKey<T> key);

  DataPromotionPolicy getDataPromotionPolicy();


  public static class GetNameConvertor extends Convertor<ModelKey<?>, String> {
    public String convert(ModelKey<?> value) {
      return value.getName();
    }
  }


  public static class GetDisplayNameConvertor extends Convertor<ModelKey<?>, String> {
    public String convert(ModelKey<?> value) {
      return value.getDisplayableName();
    }
  }


  public static class UserKeysCondition extends Condition<ModelKey<?>> {
    public boolean isAccepted(ModelKey<?> value) {
      return !value.isSystem();
    }
  }


  public static class DisplayNameComparator implements Comparator<ModelKey<?>> {
    public int compare(ModelKey<?> o1, ModelKey<?> o2) {
      return String.CASE_INSENSITIVE_ORDER.compare(o1.getDisplayableName(), o2.getDisplayableName());
    }
  }
}
