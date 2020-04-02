package com.almworks.api.dynaforms.facelets;

import com.almworks.api.application.*;
import com.almworks.api.dynaforms.AbstractWorkflowField;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.Pair;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.ComponentProperty;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.util.List;

public class ShortTextField implements AbstractWorkflowField<JTextField> {
  private static final ComponentProperty<String> ORIGINAL_TEXT = ComponentProperty.createProperty("ORIGINAL_TEXT");

  private final ModelKey<String> myModelKey;
  private final DBAttribute<String> myAttribute;

  public ShortTextField(ModelKey<String> modelKey, DBAttribute<String> attribute) {
    myModelKey = modelKey;
    myAttribute = attribute;
  }

  public void addAffectedFields(List<DBAttribute> fieldList) {
    fieldList.add(myAttribute);
  }

  public void setValue(ItemUiModel model, JTextField component) throws CantPerformExceptionExplained {
    if (component.isEnabled()) {
      String originalText = ORIGINAL_TEXT.getClientValue(component);
      String text = component.getText();
      if (!text.equals(originalText)) {
        PropertyMap map = new PropertyMap();
        myModelKey.setValue(map, text);
        myModelKey.copyValue(model.getModelMap(), map);
      }
    }
  }

  public String getSaveProblem(JTextField component, MetaInfo metaInfo) {
    return null;
  }

  public Pair<JTextField, Boolean> createEditor(Lifespan lifespan, ChangeListener changeNotifier, MetaInfo metaInfo,
      List<? extends ItemWrapper> items, PropertyMap additionalProperties)
  {
    JTextField textField = new JTextField();

    boolean editable = items.size() == 1;
    if (editable) {
      ItemWrapper wrapper = items.get(0);
      String value = myModelKey.getValue(wrapper.getLastDBValues());
      String text = value == null ? "" : value;
      textField.setText(text);
      ORIGINAL_TEXT.putClientValue(textField, text);
      if (text.length() > 0) {
        try {
          Rectangle r = textField.modelToView(0);
          if (r != null) {
            textField.scrollRectToVisible(r);
          }
        } catch (BadLocationException e) {
          // ignore
        }
      }
    }
    return Pair.create(textField, editable);
  }

  public NameMnemonic getLabel(MetaInfo metaInfo) {
    return NameMnemonic.parseString(myModelKey.getDisplayableName() + ":");
  }

  public boolean isInlineLabel() {
    return true;
  }

  public double getEditorWeightY() {
    return 0;
  }

  public JComponent getInitialFocusOwner(JTextField component) {
    return null;
  }

  public boolean isConsiderablyModified(JTextField component) {
    String originalText = ORIGINAL_TEXT.getClientValue(component);
    String currentText = component.getText();
    return currentText.trim().length() > 0 && !(currentText.equals(originalText));
  }

  public void enablePrimitive(JTextField component, boolean enabled) {
    component.setEnabled(enabled);
  }
}
