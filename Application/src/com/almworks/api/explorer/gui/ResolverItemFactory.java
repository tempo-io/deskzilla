package com.almworks.api.explorer.gui;

import com.almworks.api.application.UserChanges;

/**
 * @author : Dyoma
 */
public interface ResolverItemFactory {
  long createItem(String text, UserChanges changes);
}
