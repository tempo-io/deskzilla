package com.almworks.explorer.workflow;

import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.MetaInfo;
import com.almworks.util.Pair;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.ComponentProperty;
import com.almworks.util.ui.UIUtil;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;

abstract class SetTextParamHelper extends SetParam<String> {
  public final static ComponentProperty<JTextComponent> TEXT_COMPONENT =
    ComponentProperty.createProperty("textComponent");

  public SetTextParamHelper(String attribute, TypedKey<String> operation, NameMnemonic label) {
    super(attribute, operation, label);
  }

  protected String getValue(JComponent component) {
    return TEXT_COMPONENT.getClientValue(component).getText();
  }

  public Pair<JComponent, Boolean> createEditor(Lifespan lifespan, ChangeListener changeNotifier, MetaInfo metaInfo,
                                                List<? extends ItemWrapper> items, PropertyMap additionalProperties)
  {
    JComponent component = buildComponent(changeNotifier, items);
    return Pair.create(component, null);
  }

  private JComponent buildComponent(ChangeListener changeNotifier, List<? extends ItemWrapper> items) {
    JTextComponent textComponent = createComponent(items);
    UIUtil.addTextListener(textComponent, changeNotifier);
    JComponent component = textComponent;
    if (needsScrollpane(textComponent)) {
      component = new JScrollPane(textComponent);
      component.setPreferredSize(new Dimension(200, 150));
    }
    TEXT_COMPONENT.putClientValue(component, textComponent);
    return component;
  }

  private boolean needsScrollpane(JTextComponent component) {
    return !(component instanceof JTextField);
  }

  protected abstract JTextComponent createComponent(List<? extends ItemWrapper> items);
}
