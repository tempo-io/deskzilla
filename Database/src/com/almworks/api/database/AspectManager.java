package com.almworks.api.database;

import org.almworks.util.TypedKey;

import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface AspectManager {
  // todo aspected class move inside AspectProvider

  <T> void registerAspectProvider(Class<T> aspectedClass, AspectProvider provider);

  <K> K getAspect(Aspected aspected, TypedKey<K> key);

  Map<TypedKey, ?> copyAspects(Aspected aspected);
}
