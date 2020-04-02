package com.almworks.explorer.workflow;

import com.almworks.api.application.*;
import com.almworks.util.Pair;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.AList;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

/**
 * @author dyoma
 */
public class AddConstants<T> extends LoadedEditPrimitive<Collection<T>> {
  private final Collection<T> myValue;

  public AddConstants(String attribute, TypedKey<? extends Collection<T>> operation, Collection<T> values) {
    super(attribute, operation);
    myValue = Collections15.arrayList(values);
  }

  public Pair<JComponent, Boolean> createEditor(Lifespan lifespan, ChangeListener changeNotifier, MetaInfo metaInfo,
      List<? extends ItemWrapper> items, PropertyMap additionalProperties)
  {
    AList<Object> constants = AList.create();
    constants.setEnabled(false);
    return Pair.create((JComponent) constants, null);
  }

  public NameMnemonic getLabel(MetaInfo metaInfo) throws CantPerformExceptionExplained {
    return NameMnemonic.rawText(findKey(metaInfo).getDisplayableName());
  }

  public String getSaveProblem(JComponent component, MetaInfo metaInfo) {
    return null;
  }

  public boolean isInlineLabel() {
    return false;
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

  @Override
  public boolean isApplicable(MetaInfo metaInfo, List<ItemWrapper> items) {
    return true;
  }
}
