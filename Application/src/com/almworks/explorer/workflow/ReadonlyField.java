package com.almworks.explorer.workflow;

import com.almworks.api.application.*;
import com.almworks.api.dynaforms.AbstractWorkflowField;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.Pair;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public abstract class ReadonlyField<V> implements AbstractWorkflowField<JTextField> {
  protected final ModelKey<V> myKey;
  private final DBAttribute myAttribute;

  protected ReadonlyField(ModelKey<V> key, DBAttribute attribute) {
    myKey = key;
    myAttribute = attribute;
  }

  public String getSaveProblem(JTextField component, MetaInfo metaInfo) {
    return null;
  }

  @Nullable
  public Pair<JTextField, Boolean> createEditor(Lifespan lifespan, ChangeListener changeNotifier, MetaInfo metaInfo,
      List<? extends ItemWrapper> items, PropertyMap additionalProperties)
  {
    JTextField field = new JTextField(getDisplayedValue());
    field.setEditable(false);
    field.setEnabled(false);
    field.setMinimumSize(new Dimension(0, 0));
    return Pair.create(field, null);
  }

  protected abstract String getDisplayedValue();

  protected static NameMnemonic getLabelFromKey(ModelKey<?> key) {
    return NameMnemonic.parseString(key.getDisplayableName() + ":");
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
    return false;
  }

  public void enablePrimitive(JTextField component, boolean enabled) {
  }

  public NameMnemonic getLabel(MetaInfo metaInfo) {
    ModelKey<?> key = myKey;
    return getLabelFromKey(key);
  }

  public void addAffectedFields(List<DBAttribute> fieldList) {
    fieldList.add(myAttribute);
  }
}
