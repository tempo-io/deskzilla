package com.almworks.explorer.workflow;

import com.almworks.api.application.*;
import com.almworks.api.explorer.gui.AbstractComboBoxModelKey;
import com.almworks.util.Pair;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import java.util.List;

public class SetConstant<T> extends LoadedEditPrimitive<T> {
  private final T myValue;

  public SetConstant(String attribute, T value, TypedKey<T> operation) {
    super(attribute, operation);
    myValue = value;
  }

  public Pair<JComponent, Boolean> createEditor(Lifespan lifespan, ChangeListener changeNotifier, MetaInfo metaInfo, List<? extends ItemWrapper> items, PropertyMap additionalProperties) throws CantPerformExceptionExplained {
    JTextField textField = new JTextField(String.valueOf(myValue));
    textField.setEditable(false);
    return Pair.create((JComponent)textField, null);
  }

  public NameMnemonic getLabel(MetaInfo metaInfo) throws CantPerformExceptionExplained {
    return NameMnemonic.rawText(findKey(metaInfo).getDisplayableName() + ":");
  }

  public boolean isApplicable(MetaInfo metaInfo, List<ItemWrapper> items) {
    try {
      final ModelKey<?> modelKey = findKey(metaInfo);
      if(modelKey instanceof AbstractComboBoxModelKey) {
        final AbstractComboBoxModelKey cbmk = (AbstractComboBoxModelKey) modelKey;
        for(final ItemWrapper item : items) {
          if(!isApplicable(cbmk, item)) {
            return false;
          }
        }
      }
      return true;
    } catch (CantPerformExceptionExplained e) {
      return false;
    }
  }

  private boolean isApplicable(AbstractComboBoxModelKey cbmk, ItemWrapper item) {
    List<? extends ItemKey> list = cbmk.getResolvedVariants(item.getLastDBValues());
    if (list == null) {
      return true;
    }
    for (ItemKey key : list) {
      if(key.getId().equalsIgnoreCase(String.valueOf(myValue))) {
        return true;
      }
    }
    return false;
  }

  public String getSaveProblem(JComponent component, MetaInfo metaInfo) {
    return null;
  }

  public boolean isInlineLabel() {
    return true;
  }

  public void setValue(ItemUiModel model, JComponent component) throws CantPerformExceptionExplained {
    findOperation(model.getMetaInfo()).perform(model, myValue);
  }

  public JComponent getInitialFocusOwner(JComponent component) {
    return null;
  }

  public void enablePrimitive(JComponent component, boolean enabled) {
    component.setEnabled(enabled);
  }

  public T getValue() {
    return myValue;
  }
}
