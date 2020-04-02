package com.almworks.api.application.util;

import com.almworks.api.application.*;
import com.almworks.api.engine.Connection;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.Pair;
import com.almworks.util.TODO;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.PlainTextCanvas;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.models.ColumnSizePolicy;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.threads.CanBlock;
import org.almworks.util.*;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * @author dyoma
 */
public class BaseModelKey<T> implements ModelKey<T>, ModelKeyWithOptionalBehaviors {
  private final String myName;
  private final String myDisplayableName;
  private final CanvasRenderer<PropertyMap> myRenderer;
  private final ModelMergePolicy myMergePolicy;
  private final ColumnSizePolicy mySizePolicy;
  private final DataAccessor<T> myAccessor;
  @Nullable
  private final Comparator<T> myComparator;
  @NotNull
  private final DataIO<T> myIO;
  @Nullable
  private final Export<? super T> myExport;
  private final DataPromotionPolicy myDataPromotionPolicy;
  private final Map<? extends TypedKey, ?> myOptionalBehaviors;

  public BaseModelKey(String name, String displayableName, CanvasRenderer<PropertyMap> renderer,
    ModelMergePolicy mergePolicy, ColumnSizePolicy sizePolicy, @Nullable Comparator<T> comparator,
    @NotNull DataIO<T> io, @Nullable Export<? super T> export, @Nullable DataAccessor<T> accessor,
    @NotNull DataPromotionPolicy promotionPolicy, @Nullable Map optionalBehaviors)
  {
    myName = name;
    myDisplayableName = displayableName;
    myRenderer = renderer;
    myMergePolicy = mergePolicy;
    mySizePolicy = sizePolicy;
    myComparator = comparator;
    myIO = io;
    myExport = export;
    myAccessor = accessor != null ? accessor : new SimpleDataAccessor<T>(name);
    myDataPromotionPolicy = promotionPolicy;
    myOptionalBehaviors = optionalBehaviors == null ? Collections.emptyMap() : optionalBehaviors;
  }

  public <T> T getOptionalBehavior(TypedKey<T> key) {
    return key == null ? null : key.getFrom(myOptionalBehaviors);
  }

  /**
   * Changes the already present behavior. Does not add new behavior or remove already contained one.
   * @return true if the behaviour was previously contained in the key and was set to the specified not null value.
   * */
  public <T> boolean changeOptionalBehavior(TypedKey<T> key, @NotNull T behavior) {
    if (behavior == null || !myOptionalBehaviors.containsKey(key)) return false;
    key.putTo(myOptionalBehaviors, behavior);
    return true;
  }

  @NotNull
  public DataIO<T> getIO() {
    return myIO;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public T getValue(ModelMap model) {
    return myAccessor.getValue(model);
  }

  public <SM> SM getModel(Lifespan lifespan, ModelMap model, Class<SM> aClass) {
    return myIO.getModel(lifespan, model, this, aClass);
  }

  public boolean hasValue(ModelMap model) {
    return myAccessor.hasValue(model);
  }

  public void setValue(PropertyMap values, T value) {
    myAccessor.setValue(values, value);
  }

  public T getValue(PropertyMap values) {
    return myAccessor.getValue(values);
  }

  public boolean isEqualValue(ModelMap models, PropertyMap values) {
    return myAccessor.isEqualValue(models, values);
  }

  public boolean isEqualValue(PropertyMap values1, PropertyMap values2) {
    return myAccessor.isEqualValue(values1, values2);
  }

  public void copyValue(ModelMap to, PropertyMap from) {
    myAccessor.copyValue(to, from, this);
  }

  public boolean hasValue(PropertyMap values) {
    return myAccessor.hasValue(values);
  }

  @CanBlock
  public void addChanges(UserChanges changes) {
    myIO.addChanges(changes, this);
  }

  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    myIO.extractValue(itemVersion, itemServices, values, this);
  }

  public String getDisplayableName() {
    return myDisplayableName;
  }

  public boolean isSystem() {
    return false;
  }

  @NotNull
  public CanvasRenderer<PropertyMap> getRenderer() {
    return myRenderer;
  }

  public void takeSnapshot(PropertyMap to, ModelMap from) {
    myAccessor.takeSnapshot(to, from);
  }

  public ModelMergePolicy getMergePolicy() {
    return myMergePolicy;
  }

  public ColumnSizePolicy getRendererSizePolicy() {
    return mySizePolicy;
  }

  public Pair<String, ExportValueType> formatForExport(PropertyMap values, NumberFormat numberFormat,
    DateFormat dateFormat, boolean htmlAccepted)
  {
    return myExport != null ? myExport.formatForExport(values, this, numberFormat, dateFormat, htmlAccepted) : null;
  }

  public boolean isExportable(Collection<Connection> connections) {
    return myExport != null && myExport.isExportable(connections);
  }

  public <T> ModelOperation<T> getOperation(TypedKey<T> key) {
    throw TODO.notImplementedYet();
  }

  public int compare(T o1, T o2) {
    return myComparator != null ? myComparator.compare(o1, o2) : 0;
  }

  public DataPromotionPolicy getDataPromotionPolicy() {
    return myDataPromotionPolicy;
  }

  public String toString() {
    return myName;
  }

  public static <T> DataIO<T> emptyIO() {
    return (DataIO<T>) DataIO.EMPTY;
  }

  /**
   * @return string representation of value for text match purpose
   */
  public String matchesPatterString(PropertyMap map) {
    return myAccessor.matchesPatternString(map);
  }

  public interface DataIO<V> {
    DataIO<Object> EMPTY = new DataIO<Object>() {
      public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values, ModelKey<Object> modelKey) {
      }

      public void addChanges(UserChanges changes, ModelKey<Object> modelKey) {
        throw TODO.shouldNotHappen(modelKey.getDisplayableName());
      }

      public <SM> SM getModel(Lifespan life, ModelMap model, ModelKey<Object> modelKey, Class<SM> aClass) {
        throw TODO.shouldNotHappen(modelKey.getDisplayableName());
      }
    };

    void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values, ModelKey<V> key);

    void addChanges(UserChanges changes, ModelKey<V> key);

    <SM> SM getModel(Lifespan life, ModelMap model, ModelKey<V> key, Class<SM> aClass);
  }


  public interface DataAccessor<T> {
    T getValue(ModelMap model);

    boolean hasValue(ModelMap model);

    void setValue(PropertyMap values, T value);

    T getValue(PropertyMap values);

    void copyValue(ModelMap to, PropertyMap from, ModelKey<T> key);

    boolean hasValue(PropertyMap values);

    void takeSnapshot(PropertyMap to, ModelMap from);

    boolean isEqualValue(ModelMap models, PropertyMap values);

    boolean isEqualValue(PropertyMap values1, PropertyMap values2);

    /**
     * @return string representation of value for text match purpose.<br>
     * null return value means that default algorithm should be applied.<br>
     * If there is no value that can match a pattern the empty string should be returned
     */
    String matchesPatternString(PropertyMap map);
  }


  public static class SimpleDataAccessor<T> implements DataAccessor<T> {
    private final TypedKey<T> myKey;

    public SimpleDataAccessor(String name) {
      myKey = TypedKey.create(name);
    }

    public SimpleDataAccessor(TypedKey<T> key) {
      myKey = key;
    }

    public T getValue(ModelMap model) {
      return model.get(myKey);
    }

    public void setValue(PropertyMap values, T value) {
      values.put(myKey, value);
    }

    public T getValue(PropertyMap values) {
      return values.get(myKey);
    }

    public void copyValue(ModelMap to, PropertyMap from, ModelKey<T> key) {
      if (isEqualValue(to, from))
        return;
      setValue(to, getValue(from));
      to.valueChanged(key);
    }

    protected final void setValue(ModelMap to, T value) {
      to.put(myKey, value);
    }

    public boolean hasValue(ModelMap model) {
      return isExistingValue(getValue(model));
    }

    public boolean hasValue(PropertyMap values) {
      return isExistingValue(values.get(myKey));
    }

    protected boolean isExistingValue(T value) {
      return getCanonicalValueForComparison(value) != null;
    }

    public void takeSnapshot(PropertyMap to, ModelMap from) {
      setValue(to, getValue(from));
    }

    public boolean isEqualValue(ModelMap models, PropertyMap values) {
      T v1 = getValue(values);
      T v2 = getValue(models);
      return isEqualValue(v1, v2);
    }

    public boolean isEqualValue(PropertyMap values1, PropertyMap values2) {
      T v1 = getValue(values1);
      T v2 = getValue(values2);
      return isEqualValue(v1, v2);
    }

    @Override
    public String matchesPatternString(PropertyMap map) {
      return null;
    }

    protected boolean isEqualValue(@Nullable T v1, @Nullable T v2) {
      return Util.equals(getCanonicalValueForComparison(v1), getCanonicalValueForComparison(v2));
    }

    protected Object getCanonicalValueForComparison(@Nullable T value) {
      if (value == null)
        return value;
      if (value instanceof String && ((String) value).length() == 0)
        return null;
      if (value instanceof Object[] && ((Object[])value).length == 0)
        return null;
      if (value instanceof Collection && ((Collection)value).isEmpty())
        return null;
      return value;
    }

    public static <T> DataAccessor<List<T>> createCopyList(String name) {
      return new SimpleDataAccessor<List<T>>(name) {
        @Override
        public List<T> getValue(ModelMap model) {
          return createCopy(super.getValue(model));
        }

        @Override
        public List<T> getValue(PropertyMap values) {
          return createCopy(super.getValue(values));
        }

        private List<T> createCopy(List<T> value) {
          return value != null ? Collections15.arrayList(value) : null;
        }
      };
    }
  }

  public static class SimpleRefListAccessor extends SimpleDataAccessor<List<ItemKey>> {
    public SimpleRefListAccessor(String name) {
      super(name);
    }

    @Override
    protected Object getCanonicalValueForComparison(@Nullable List<ItemKey> value) {
      if (value == null || value.isEmpty())
        return super.getCanonicalValueForComparison(value);
      List<ItemKey> copy = Collections15.arrayList(value);
      Collections.sort(copy, new Comparator<ItemKey>() {
        public int compare(ItemKey o1, ItemKey o2) {
          long res = getItemKey(o1) - getItemKey(o2);
          return res == 0 ? 0 : (res < 0 ? -1 : 1);
        }

        private long getItemKey(ItemKey key) {
          return key == null ? -1 : key.getResolvedItem();
        }
      });
      return copy;
    }
  }


  public interface Export<T> {
    Export<String> STRING = new StringExport(ExportValueType.STRING);
    Export<String> LARGE_STRING = new StringExport(ExportValueType.LARGE_STRING);
    Export RENDER = new RenderExport();
    Export<Number> NUMBER = new NumberExport();
    Export<Date> DATE = new DateExport();
    Export<Integer> SECONDS_TO_HOURS_NUMBER = new SecondsToHoursNumberExport();

    boolean isExportable(Collection<Connection> connections);

    @Nullable
    Pair<String, ExportValueType> formatForExport(PropertyMap values, ModelKey<? extends T> key,
      NumberFormat numberFormat, DateFormat dateFormat, boolean htmlAccepted);

    abstract class Simple<T> implements Export<T> {
      private final ExportValueType myValueType;

      public Simple(ExportValueType valueType) {
        myValueType = valueType;
      }

      @Override
      public boolean isExportable(Collection<Connection> connections) {
        return true;
      }

      public final Pair<String, ExportValueType> formatForExport(PropertyMap values, ModelKey<? extends T> key,
        NumberFormat numberFormat, DateFormat dateFormat, boolean htmlAccepted)
      {
        T value = key.hasValue(values) ? key.getValue(values) : null;
        String str = value != null ? convertToString(value, numberFormat, dateFormat) : "";
        return Pair.create(str, myValueType);
      }

      protected abstract String convertToString(T value, NumberFormat numberFormat, DateFormat dateFormat);
    }


    class StringExport extends Simple<String> {
      public StringExport(ExportValueType valueType) {
        super(valueType);
      }

      protected String convertToString(String value, NumberFormat numberFormat, DateFormat dateFormat) {
        return value;
      }
    }


    class RenderExport<T> implements Export<T> {
      @Override
      public boolean isExportable(Collection<Connection> connections) {
        return true;
      }

      public Pair formatForExport(PropertyMap values, ModelKey key, NumberFormat numberFormat, DateFormat dateFormat,
        boolean htmlAccepted)
      {
        PlainTextCanvas canvas = new PlainTextCanvas();
        key.getRenderer().renderStateOn(CellState.LABEL, canvas, values);
        return Pair.create(canvas.getText(), ExportValueType.STRING);
      }
    }


    public static class NumberExport extends Simple<Number> {
      public NumberExport() {
        super(ExportValueType.NUMBER);
      }

      protected String convertToString(Number value, NumberFormat numberFormat, DateFormat dateFormat) {
        return numberFormat.format(value);
      }
    }


    public static class DateExport extends Simple<Date> {
      public DateExport() {
        super(ExportValueType.DATE);
      }

      protected String convertToString(Date value, NumberFormat numberFormat, DateFormat dateFormat) {
        return dateFormat.format(new Date(value.getTime()));
      }
    }

    public static class SecondsToHoursNumberExport extends Simple<Integer> {
      public SecondsToHoursNumberExport() {
        super(ExportValueType.NUMBER);
      }

      // gets time in seconds, exports number of hours
      protected String convertToString(Integer value, NumberFormat numberFormat, DateFormat dateFormat) {
        if (value == null) return "";
        return numberFormat.format(((double)value) / 3600.0);
      }
    }
  }
}
