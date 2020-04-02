package com.almworks.explorer.workflow;

import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.MetaInfo;
import com.almworks.spellcheck.SpellCheckManager;
import com.almworks.util.Pair;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.i18n.Local;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.ComponentProperty;
import com.almworks.util.ui.UIUtil;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import org.almworks.util.TypedKey;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Text area editor, possibly with a checkbox beneath it.
 * @param <T>
 */
class AddTextAreaParam<T> extends SetParam<Collection<T>> {
  /**
   * If checkbox label is null, checkbox is not used.
   */
  @Nullable
  private final NameMnemonic myCheckboxLabel;
  /**
   * If checkbox is null, what value to set as default
   */
  private final boolean myDefaultValue;

  private final static ComponentProperty<JCheckBox> CHECK_BOX = ComponentProperty.createProperty("checkBox");

  private AddTextAreaParam(String attribute, TypedKey<? extends Collection<T>> operation, NameMnemonic label, String checkboxLabel, boolean defaultValue) {
    super(attribute, operation, label);
    myCheckboxLabel = checkboxLabel != null ? NameMnemonic.parseString(Local.parse(checkboxLabel)) : null;
    assert myCheckboxLabel == null || !myCheckboxLabel.getText().trim().isEmpty();
    myDefaultValue = defaultValue;
  }

  public static AddTextAreaParam<String> textEditor(String attribute, TypedKey<? extends Collection<String>> operation, NameMnemonic label) {
    return new AddTextAreaParam<String>(attribute, operation, label, null, false);
  }

  public static AddTextAreaParam<Pair<String, Boolean>> textEditorWithCheckbox(String attribute, TypedKey<? extends Collection<Pair<String, Boolean>>> operation, NameMnemonic label, @NotNull String checkboxLabel, boolean defaultValue) {
    assert checkboxLabel != null;
    return new AddTextAreaParam<Pair<String, Boolean>>(attribute, operation, label, checkboxLabel, defaultValue);
  }

  public Pair<JComponent, Boolean> createEditor(Lifespan lifespan, ChangeListener changeNotifier, MetaInfo metaInfo,
      List<? extends ItemWrapper> items, PropertyMap additionalProperties)
  {
    JTextPane area = new JTextPane();
    SpellCheckManager.attach(lifespan, area);
    lifespan.add(UIUtil.addTextListener(area, changeNotifier));
    JComponent component = new JScrollPane(area);
    int prefHeight = 5 * UIUtil.getLineHeight(area);
    component.setPreferredSize(new Dimension(200, prefHeight));
    if (myCheckboxLabel != null) {
      component = addCheckbox(component);
    }
    SetTextParamHelper.TEXT_COMPONENT.putClientValue(component, area);
    return Pair.create(component, null);
  }

  private JComponent addCheckbox(JComponent component) {
    assert myCheckboxLabel != null;
    JCheckBox jcb = new JCheckBox();
    // align checkbox with the text area above
    Insets ins = jcb.getInsets();
    jcb.setBorder(new EmptyBorder(ins.top, 0, ins.bottom, ins.right));
    myCheckboxLabel.setToButton(jcb);
    jcb.setSelected(myDefaultValue);
    FormLayout layout = new FormLayout("d:grow", "f:d:grow, d");
    PanelBuilder b = new PanelBuilder(layout);
    CellConstraints cc = new CellConstraints();
    b.add(component, cc.xy(1, 1));
    b.add(jcb, cc.xy(1, 2));
    component = b.getPanel();
    CHECK_BOX.putClientValue(component, jcb);
    return component;
  }

  protected Collection<T> getValue(JComponent component) {
    JTextComponent textComponent = getTextComponent(component);
    if (textComponent == null) return Collections.emptyList();
    String text = textComponent.getText();
    Collection<T> ret;
    if (myCheckboxLabel == null) {
      ret = (Collection)Collections.singleton(text);
    } else {
      JCheckBox jcb = CHECK_BOX.getClientValue(component);
      assert jcb != null : component + " " + myCheckboxLabel;
      boolean checkboxValue = (jcb != null) ? jcb.isSelected() : myDefaultValue;
      ret = (Collection)Collections.singleton(Pair.create(text, checkboxValue));
    }
    assert check(ret);
    return ret;
  }
  
  private boolean check(Collection<?> returnValue) {
    // cast asserts the type
    getOperation().cast(returnValue);
    return true;
  }

  public boolean isConsiderablyModified(JComponent component) {
    JTextComponent textComponent = getTextComponent(component);
    return textComponent != null && !textComponent.getText().trim().isEmpty();
  }

  public boolean isInlineLabel() {
    return true;
  }

  @Override
  public double getEditorWeightY() {
    return 1;
  }

  public void enablePrimitive(JComponent component, boolean enabled) {
    component.setEnabled(enabled);
  }

  @Nullable
  private JTextComponent getTextComponent(JComponent component) {
    final JTextComponent c = SetTextParamHelper.TEXT_COMPONENT.getClientValue(component);
    assert c != null : component;
    return c;
  }

  @Override
  public JComponent getInitialFocusOwner(JComponent component) {
    return Util.NN(getTextComponent(component), component);
  }
}
