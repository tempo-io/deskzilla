package com.almworks.explorer.workflow;

import com.almworks.api.application.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.Collections15;

import javax.swing.*;
import java.util.List;

public class ReadonlyEnumSetAddField extends ReadonlyEnumSetField {
  public ReadonlyEnumSetAddField(ModelKey<List<ItemKey>> key, List<ResolvedItem> target,
    DBAttribute attribute)
  {
    super(key, target, attribute);
  }

  public void setValue(ItemUiModel model, JTextField component) throws CantPerformExceptionExplained {
    ModelMap map = model.getModelMap();
    PropertyMap props = new PropertyMap();
    if (!myTarget.isEmpty()) {
      myKey.takeSnapshot(props, map);
      List<ItemKey> oldValue = myKey.getValue(props);
      List<ItemKey> newValue = Collections15.arrayList(oldValue);
      for (ResolvedItem target : myTarget) {
        if (!newValue.contains(target)) {
          newValue.add(target);
        }
      }
      myKey.setValue(props, newValue);
      myKey.copyValue(map, props);
    }
  }

  protected String getDisplayedValue() {
    return "+ " + super.getDisplayedValue();
  }
}