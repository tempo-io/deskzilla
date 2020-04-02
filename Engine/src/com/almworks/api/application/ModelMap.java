package com.almworks.api.application;

import com.almworks.util.collections.Modifiable;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.TypedKey;

import java.util.Collection;

/**
 * @author : Dyoma
 */
public interface ModelMap extends Modifiable {
  <T> T get(TypedKey<? extends T> key);

  <T> void put(TypedKey<T> key, T value);

  void registerKey(String name, ModelKey<?> key);

  void valueChanged(ModelKey<?> key);

  MetaInfo getMetaInfo();

  boolean copyFrom(PropertyMap newValues);

  Collection<? extends ModelKey> getAllKeys();

  <T> T getService(TypedKey<T> key);
}
