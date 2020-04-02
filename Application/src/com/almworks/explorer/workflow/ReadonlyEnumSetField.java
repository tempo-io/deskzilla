package com.almworks.explorer.workflow;

import com.almworks.api.application.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.Util;

import javax.swing.*;
import java.util.List;

public class ReadonlyEnumSetField extends ReadonlyField<List<ItemKey>> {
  protected final List<ResolvedItem> myTarget;

  public ReadonlyEnumSetField(ModelKey<List<ItemKey>> key, List<ResolvedItem> target,
    DBAttribute attribute)
  {
    super(key, attribute);
    myTarget = target;
  }

  public void setValue(ItemUiModel model, JTextField component) throws CantPerformExceptionExplained {
    ModelMap map = model.getModelMap();
    List<ItemKey> oldValue = myKey.getValue(map);
    PropertyMap props = new PropertyMap();
    if (!Util.equals(oldValue, myTarget)) {
      myKey.takeSnapshot(props, map);
      myKey.setValue(props, (List)myTarget);
      myKey.copyValue(map, props);
    }
  }

  protected String getDisplayedValue() {
    if (myTarget.isEmpty())
      return "None";
    StringBuilder b = new StringBuilder();
    for (ResolvedItem ra : myTarget) {
      if (b.length() > 0)
        b.append(", ");
      b.append(ra.getDisplayName());
    }
    return b.toString();
  }
}