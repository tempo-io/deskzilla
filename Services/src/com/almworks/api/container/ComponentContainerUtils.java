package com.almworks.api.container;

import com.almworks.util.commons.Factory;

/**
 * @author dyoma
 */
public class ComponentContainerUtils {
  public static <T> Factory<T> instantiatingFactory(final ComponentContainer container, final Class<? extends T> componentClass, final String selector) {
    return new Factory<T>() {
      public T create() {
        return container.instantiate(componentClass, selector);
      }
    };
  }

  public static <T> Factory<T> instantiatingFactory(final ComponentContainer container, final Class<T> componentClass) {
    return new Factory<T>() {
      public T create() {
        return container.instantiate(componentClass);
      }
    };
  }
}
