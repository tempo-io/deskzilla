package com.almworks.api.application;

import com.almworks.items.api.DBReader;
import com.almworks.util.threads.ThreadSafe;
import org.almworks.util.TypedKey;

/**
 * @author dyoma
 */
public interface ItemsCollector {
  void addItem(long item, DBReader reader);

  void removeItem(long item);

  <T> T getValue(TypedKey<T> key);

  <T> T putValue(TypedKey<T> key, T value);

  @ThreadSafe
  void reportError(String error);
}
