package com.almworks.api.explorer.gui;

import com.almworks.api.application.*;
import com.almworks.util.advmodel.AListModel;
import org.almworks.util.detach.Lifespan;

/**
 * @author : Dyoma
 */
public interface ItemModelKey<T> extends ModelKey<T> {
  TextResolver getResolver();

  AListModel<ItemKey> getModelVariants(ModelMap model, Lifespan life);
}
