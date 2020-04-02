package com.almworks.explorer.workflow;

import com.almworks.api.application.ItemWrapper;
import com.almworks.util.text.NameMnemonic;
import org.almworks.util.TypedKey;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.util.List;

class SetTextFieldParam extends SetTextParamHelper {
  public SetTextFieldParam(String attribute, TypedKey<String> operation, NameMnemonic label) {
    super(attribute, operation, label);
  }

  protected JTextComponent createComponent(List<? extends ItemWrapper> items) {
    return new JTextField();
  }

  public boolean isInlineLabel() {
    return true;
  }

  public void enablePrimitive(JComponent component, boolean enabled) {
    component.setEnabled(enabled);
  }
}
