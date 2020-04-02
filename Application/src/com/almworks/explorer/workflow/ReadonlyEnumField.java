package com.almworks.explorer.workflow;

import com.almworks.api.application.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.Util;

import javax.swing.*;

public class ReadonlyEnumField extends ReadonlyField<ItemKey> {
  private final ResolvedItem myTarget;

  public ReadonlyEnumField(ModelKey<ItemKey> key, ResolvedItem target, DBAttribute attribute) {
    super(key, attribute);
    myTarget = target;
  }

  public void setValue(ItemUiModel model, JTextField component) throws CantPerformExceptionExplained {
    ModelMap map = model.getModelMap();
    ItemKey oldValue = myKey.getValue(map);
    PropertyMap props = new PropertyMap();
    if (!Util.equals(oldValue, myTarget)) {
      myKey.takeSnapshot(props, map);
      myKey.setValue(props, myTarget);
      myKey.copyValue(map, props);
    }
  }

  protected String getDisplayedValue() {
    return myTarget.getDisplayName();
  }
}
