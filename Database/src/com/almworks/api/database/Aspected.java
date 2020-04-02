package com.almworks.api.database;

import org.almworks.util.TypedKey;

import java.util.Map;


/**
 * :todoc:
 *
 * @author sereda
 */
public interface Aspected {
  <K> K getAspect(TypedKey<K> key);

  Map<TypedKey, ?> copyAspects();
}
