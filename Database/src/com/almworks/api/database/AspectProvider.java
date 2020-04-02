package com.almworks.api.database;

import org.almworks.util.TypedKey;

import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface AspectProvider {
  <K> K getAspect(Aspected object, TypedKey<K> key);

  void copyAspects(Aspected object, Map<TypedKey, ?> receptacle);
}
