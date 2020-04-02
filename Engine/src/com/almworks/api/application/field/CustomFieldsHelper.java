package com.almworks.api.application.field;

import com.almworks.api.application.*;
import com.almworks.api.application.util.ModelKeyUtils;
import com.almworks.api.application.util.PredefinedKey;
import com.almworks.items.sync.ItemVersion;
import com.almworks.spi.provider.ConnectionContext;
import com.almworks.util.commons.Function;
import com.almworks.util.properties.PropertyMap;
import gnu.trove.THashMap;
import org.almworks.util.*;
import org.jetbrains.annotations.*;
import util.external.BitSet2;

import java.util.*;

public class CustomFieldsHelper {
  public static final ModelKey<Map<ModelKey<?>, ItemField<?, ?>>> KEYS_ATTRIBUTES =
    PredefinedKey.create("keyAttributes");
  public static final TypedKey<Map<Map<ModelKey<?>, ItemField<?, ?>>, Map<ModelKey<?>, ItemField<?, ?>>>>
    CUSTOM_FIELD_KEY_ATTRIBUTES_CACHE = TypedKey.create("CUSTOM_FIELD_KEY_ATTRIBUTES_CACHE");
  public static final Function<Map<ModelKey<?>, ItemField<?, ?>>, Map<ModelKey<?>, ItemField<?, ?>>>
    CUSTOM_FIELD_KEY_ATTRIBUTES_CACHE_CONVERTOR =
    new Function<Map<ModelKey<?>, ItemField<?, ?>>, Map<ModelKey<?>, ItemField<?, ?>>>() {
      public Map<ModelKey<?>, ItemField<?, ?>> invoke(Map<ModelKey<?>, ItemField<?, ?>> argument) {
        return Collections.unmodifiableMap(argument);
      }
    };
  public static final TypedKey<Map<List<ModelKey<?>>, List<ModelKey<?>>>> CUSTOM_FIELD_STRUCTURE_KEY_CACHE =
    TypedKey.create("CUSTOM_FIELD_STRUCTURE_KEY_CACHE");
  public static final Function<List<ModelKey<?>>, List<ModelKey<?>>> CUSTOM_FIELD_STRUCTURE_KEY_CACHE_CONVERTOR =
    new Function<List<ModelKey<?>>, List<ModelKey<?>>>() {
      public List<ModelKey<?>> invoke(List<ModelKey<?>> argument) {
        return Collections.unmodifiableList(argument);
      }
    };

  @Nullable
  public static <A extends ItemField<?, ?>> A getField(ModelKey<?> key, ModelMap model) {
    Map<ModelKey<?>, ItemField<?, ?>> map = KEYS_ATTRIBUTES.getValue(model);
    assert map != null : model;
    ItemField<?, ?> r = map.get(key);
    A attr = (A) r;
    assert attr != null : key;
    return attr;
  }

  @Nullable
  public static <A extends ItemField<?, ?>> A getField(ModelKey<?> key, PropertyMap model) {
    Map<ModelKey<?>, ItemField<?, ?>> map = KEYS_ATTRIBUTES.getValue(model);
    assert map != null : model;
    ItemField<?, ?> r = map.get(key);
    A attr = (A) r;
    assert attr != null : key;
    return attr;
  }

  public static void setCustomFieldsKey(ConnectionContext context, ModelKey<List<ModelKey<?>>> key,
    PropertyMap values, List<ModelKey<?>> result, Map<ModelKey<?>, ItemField<?, ?>> keyAttrs)
  {
    Map<List<ModelKey<?>>, List<ModelKey<?>>> cache1 = context.getConnectionWideCache(CUSTOM_FIELD_STRUCTURE_KEY_CACHE);
    result = ModelKeyUtils.replaceFastCached(cache1, result, CUSTOM_FIELD_STRUCTURE_KEY_CACHE_CONVERTOR);
    key.setValue(values, result);

    Map<Map<ModelKey<?>, ItemField<?, ?>>, Map<ModelKey<?>, ItemField<?, ?>>> cache2 =
      context.getConnectionWideCache(CUSTOM_FIELD_KEY_ATTRIBUTES_CACHE);
    keyAttrs = ModelKeyUtils.replaceFastCached(cache2, keyAttrs, CUSTOM_FIELD_KEY_ATTRIBUTES_CACHE_CONVERTOR);

    KEYS_ATTRIBUTES.setValue(values, keyAttrs);
  }

  public static void extractCustomFields(ModelKey<List<ModelKey<?>>> key, ConnectionContext connectionContext, PropertyMap values, Collection<? extends ItemField> fields, ItemVersion itemVersion, LoadedItemServices itemServices) {
    int fieldsCount = fields.size();
    List<ModelKey<?>> result;
    Map<ModelKey<?>, ItemField<?, ?>> keyAttrs;
    if (fieldsCount == 0) {
      result = Collections15.emptyList();
      keyAttrs = Collections15.emptyMap();
    } else {
      result = Collections15.arrayList();
      keyAttrs = new THashMap(fieldsCount);
      BitSet2 addKeys = new BitSet2();
      for (ItemField customField : fields) {
        loadValue(customField, itemVersion, itemServices, result, values);
        ModelKey<?> modelKey = customField.getModelKey();
        ModelKeySetUtil.addKey(addKeys, modelKey);
        keyAttrs.put(modelKey, customField);
      }
      ModelKeySetUtil.addKey(addKeys, KEYS_ATTRIBUTES);
      ModelKeySetUtil.addKeysToMap(values, addKeys);
    }

    setCustomFieldsKey(connectionContext, key, values, result, keyAttrs);
  }

  public static <T> void loadValue(ItemField field, ItemVersion version, LoadedItemServices itemServices, List<ModelKey<?>> result, PropertyMap values) {
    Object value = field.loadValue(version, itemServices);
    ModelKey cfKey = field.getModelKey();
    result.add(cfKey);
    try {
      cfKey.setValue(values, value);
    } catch (ClassCastException e) {
      Log.error(e);
    }
  }
}
