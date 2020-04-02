package com.almworks.bugzilla.provider.workflow;

import com.almworks.api.application.*;
import com.almworks.api.explorer.gui.AbstractComboBoxModelKey;
import com.almworks.explorer.workflow.SetConstant;
import com.almworks.util.Pair;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.Convertor;
import com.almworks.util.commons.Condition;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

import static com.almworks.util.collections.Functional.selectMany;

public class ValueReportingEditPrimitive extends SetConstant<String> {
  public ValueReportingEditPrimitive(String attribute, String value) {
    super(attribute, value, ModelOperation.SET_STRING_VALUE);
  }

  @Override
  public Pair<JComponent, Boolean> createEditor(Lifespan lifespan, ChangeListener changeNotifier, MetaInfo metaInfo, List<? extends ItemWrapper> items, PropertyMap additionalProperties) throws CantPerformExceptionExplained {
    ModelKey<ItemKey> myKey = (ModelKey<ItemKey>)findKey(metaInfo);
    ItemKey key = findValue(myKey, items);
    if (key != null) {
      myKey.setValue(additionalProperties, key);
    }
    return super.createEditor(lifespan, changeNotifier, metaInfo, items, additionalProperties);
  }

  @Nullable
  private ItemKey findValue(ModelKey<ItemKey> myKey, List<? extends ItemWrapper> items) {
    if (!(myKey instanceof AbstractComboBoxModelKey)) {
      assert false : myKey;
      return null;
    }
    final AbstractComboBoxModelKey myComboKey = (AbstractComboBoxModelKey)myKey;
    Convertor<ItemWrapper, Collection<ItemKey>> values = ItemWrapper.GET_LAST_DB_VALUES.composition(new Convertor<PropertyMap, Collection<ItemKey>>() {
      @Override
      public Collection<ItemKey> convert(PropertyMap value) {
        return (Collection)myComboKey.getResolvedVariants(value);
      }
    });
    return Condition.<ItemKey>always().detect(selectMany(items, values), ItemKey.DISPLAY_NAME, getValue());
  }

}
