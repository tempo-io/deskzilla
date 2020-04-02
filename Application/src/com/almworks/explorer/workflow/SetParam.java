package com.almworks.explorer.workflow;

import com.almworks.api.application.*;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.TypedKey;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.List;

abstract class SetParam<T> extends LoadedEditPrimitive<T> {
  private final NameMnemonic myLabel;

  public SetParam(String attribute, TypedKey<? extends T> operation, NameMnemonic label) {
    super(attribute, operation);
    myLabel = label;
  }

  @Nullable
  protected abstract T getValue(JComponent component);

  public NameMnemonic getLabel(MetaInfo metaInfo) {
    return myLabel;
  }

  public String getSaveProblem(JComponent component, MetaInfo metaInfo) {
    try {
      String problem = findOperation(metaInfo).getArgumentProblem(getValue(component));
      if (problem == null || problem.length() > 0)
        return problem;
      return "Invalid value for " + Util.lower(getAttributeName());
    } catch (CantPerformExceptionExplained e) {
      return e.getMessage();
    }
  }

  public void setValue(ItemUiModel model, JComponent component) throws CantPerformExceptionExplained {
    findOperation(model.getMetaInfo()).perform(model, getValue(component));
  }

  public JComponent getInitialFocusOwner(JComponent component) {
    return component;
  }

  @Override
  public boolean isApplicable(MetaInfo metaInfo, List<ItemWrapper> items) {
    return true;
  }
}
