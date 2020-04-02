package com.almworks.api.application.util;

import com.almworks.api.application.*;
import com.almworks.api.engine.Connection;
import com.almworks.spi.provider.AbstractConnection;
import com.almworks.spi.provider.BaseConnectionContext;
import com.almworks.util.Pair;
import com.almworks.util.collections.Convertor;
import com.almworks.util.commons.Function;
import com.almworks.util.commons.Procedure2;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.PlainTextCanvas;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.*;

public class ModelKeyUtils {
  private static final ThreadLocal<PlainTextCanvas> myCanvases = new PlainTextCanvas.ThreadLocalFactory();

  static final TypedKey<Map<List<ItemKey>, List<ItemKey>>> SINGLE_REF_LIST_IO_CACHE =
    TypedKey.create("SINGLE_REF_LIST_IO_CACHE");

  static final Function<List<ItemKey>, List<ItemKey>> SINGLE_REF_LIST_IO_CACHE_CONVERTOR =
      new Function<List<ItemKey>, List<ItemKey>>() {
        public List<ItemKey> invoke(List<ItemKey> argument) {
          return Collections.unmodifiableList(argument);
        }
      };

  static final TypedKey<Map<Set<ItemKey>, Set<ItemKey>>> SINGLE_REF_SET_IO_CACHE =
    TypedKey.create("SINGLE_REF_SET_IO_CACHE");

  static final Function<Set<ItemKey>, Set<ItemKey>> SINGLE_REF_SET_IO_CACHE_CONVERTOR =
      new Function<Set<ItemKey>, Set<ItemKey>>() {
        public Set<ItemKey> invoke(Set<ItemKey> argument) {
          return Collections.unmodifiableSet(argument);
        }
      };

  static final Function<List<ItemKey>, List<ItemKey>> LIST_SORTER = new Function<List<ItemKey>, List<ItemKey>>() {
    @Override
    public List<ItemKey> invoke(List<ItemKey> argument) {
      Collections.sort(argument, ItemKey.keyComparator());
      return argument;
    }
  };

  static final Function<Set<ItemKey>, Set<ItemKey>> SET_SORTER = new Function<Set<ItemKey>, Set<ItemKey>>() {
    @Override
    public Set<ItemKey> invoke(Set<ItemKey> argument) {
      return Collections15.treeSet(argument, ItemKey.keyComparator());
    }
  };

  public static final GenericToStringConvertor ANY_TO_STRING = new GenericToStringConvertor();

  @NotNull
  public static Pair<String, ExportValueType> getRenderedString(@NotNull ModelKey<?> key, @NotNull PropertyMap values) {
    CanvasRenderer<PropertyMap> renderer = key.getRenderer();
    PlainTextCanvas canvas = myCanvases.get();
    renderer.renderStateOn(CellState.LABEL, canvas, values);
    String text = canvas.getText();
    canvas.clear();
    return Pair.create(text, ExportValueType.STRING);
  }

  @Nullable
  public static Pair<String,ExportValueType> getFormattedString(ModelKey<?> key, PropertyMap values, NumberFormat numberFormat,
    DateFormat dateFormat, boolean isMultipleString)
  {
    Object value = key.getValue(values);
    if (value == null)
      return null;
    if (value instanceof Number) {
      String formatted;
      if (value instanceof BigDecimal) {
        BigDecimal decimal = ((BigDecimal) value);
        formatted = numberFormat.format(decimal.doubleValue());
      } else if (value instanceof BigInteger) {
        BigInteger integer = ((BigInteger) value);
        if (integer.bitLength() < 64)
          formatted = numberFormat.format(integer.longValue());
        else
          formatted = numberFormat.format(integer.doubleValue());
      } else if (value instanceof Float || value instanceof Double) {
        formatted = numberFormat.format(((Number) value).doubleValue());
      } else {
        formatted = numberFormat.format(((Number) value).longValue());
      }
      return Pair.create(formatted, ExportValueType.NUMBER);
    } else if (value instanceof Date) {
      String formatted = dateFormat.format(new Date(((Date) value).getTime()));
      return Pair.create(formatted, ExportValueType.DATE);
    } else {
      return Pair.create(value.toString(), isMultipleString ? ExportValueType.LARGE_STRING : ExportValueType.STRING );
    }
  }

  public static <Z> Z replaceFastCached(Map<Z, Z> cache, Z value, Function<Z, Z> convertor) {
    Z result = cache.get(value);
    if (result == null) {
      synchronized (cache) {
        result = cache.get(value);
        if (result == null) {
          result = convertor.invoke(value);
          if (result != null) {
            cache.put(result, result);
          }
        }
      }
    }
    return result;
  }

  public static <C extends Collection<ItemKey>> C getOptimizedEnumCollection(
    LoadedItemServices services, C result, C empty, TypedKey<Map<C, C>> cacheKey, Function<C, C> cacheConvertor,
    Function<C, C> sorter)
  {
    assert empty.isEmpty();
    int resultSize = result.size();
    if (resultSize == 0) {
      return empty;
    } else if (resultSize == 1) {
      Connection connection = services.getConnection();
      if (connection == null) {
        return empty;
      }
      if (!(connection instanceof AbstractConnection)) {
        assert false : connection;
        return result;
      }
      BaseConnectionContext context = ((AbstractConnection) connection).getContext();
      Map<C, C> arrayCache = context.getConnectionWideCache(cacheKey);
      result = replaceFastCached(arrayCache, result, cacheConvertor);
      return result;
    } else {
      return sorter.invoke(result);
    }
  }

  public static List<ItemKey> getOptimizedEnumList(LoadedItemServices itemServices, List<ItemKey> result) {
    return getOptimizedEnumCollection(
      itemServices, result, Collections15.<ItemKey>emptyList(), 
      SINGLE_REF_LIST_IO_CACHE, SINGLE_REF_LIST_IO_CACHE_CONVERTOR, LIST_SORTER);
  }

  public static Set<ItemKey> getOptimizedEnumSet(LoadedItemServices itemServices, Set<ItemKey> result) {
    return getOptimizedEnumCollection(
      itemServices, result, Collections15.<ItemKey>emptySet(),
      SINGLE_REF_SET_IO_CACHE, SINGLE_REF_SET_IO_CACHE_CONVERTOR, SET_SORTER);
  }

  public static <T> void setModelValue(ModelKey<T> key, T value, ModelMap model) {
    PropertyMap map = new PropertyMap();
    key.setValue(map, value);
    if (key.isEqualValue(model, map))
      return;
    key.copyValue(model, map);
  }

  public static <T> Procedure2<T, ModelMap> update(final ModelKey<T> key) {
    return new Procedure2<T, ModelMap>() {
      @Override
      public void invoke(T t, ModelMap model) {
        setModelValue(key, t, model);
      }
    };
  }

  public static class GenericToStringConvertor extends Convertor<Object, String> {
    @Override
      public String convert(Object value) {
      if (value == null)
        return "";
      if (value instanceof String)
        return (String) value;
      return String.valueOf(value);
    }
  }
}