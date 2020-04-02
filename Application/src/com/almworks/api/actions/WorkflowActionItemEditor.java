package com.almworks.api.actions;

import com.almworks.api.application.*;
import com.almworks.api.dynaforms.EditPrimitive;
import com.almworks.util.Pair;
import com.almworks.util.collections.*;
import com.almworks.util.components.ASeparator;
import com.almworks.util.components.ScrollablePanel;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.model.ValueModel;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.DocumentFormAugmentor;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import com.almworks.util.ui.swing.AwtUtil;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.DetachComposite;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Map;

public class WorkflowActionItemEditor implements ItemEditorUi {
  private static final Insets EMPTY_INSETS = new Insets(0, 0, 0, 0);
  private static final Insets TOP_GAP = new Insets(5, 0, 0, 0);
  private static final Insets TOP_LABEL_INSETS = new Insets(2, 0, 0, 5);
  private static final Insets NOT_TOP_LABEL_INSETS = new Insets(7, 0, 0, 5);

  private static final int VERTICAL_GAP;
  private static final Insets SEPARATOR_GAP;
  static {
    final int lineHeight = UIUtil.getLineHeight(new JLabel("Foo"));
    VERTICAL_GAP = Math.round(lineHeight * 1.2f);
    SEPARATOR_GAP = new Insets(VERTICAL_GAP, 0, Math.round(lineHeight / 3f), 0);
  }

  private final SimpleModifiable myModifiable = new SimpleModifiable();
  private final JComponent myPanel = new JPanel(new GridBagLayout());
  private final JScrollPane myScrollpane = new JScrollPane(ScrollablePanel.create(myPanel));

  private final Map<EditPrimitive, JComponent> myComponents = Collections15.hashMap();
  private final Map<EditPrimitive, JCheckBox> myCheckBoxes = Collections15.hashMap();

  @Nullable
  private JComponent myInitialFocusOwner;

  private final MetaInfo myMetaInfo;

  private final DetachComposite myLife = new DetachComposite();
  private final boolean mySingleArtifact;

  public WorkflowActionItemEditor(
    List<? extends EditPrimitive> editScript, MetaInfo metaInfo,
    List<? extends ItemWrapper> items) throws CantPerformExceptionExplained
  {
    myMetaInfo = metaInfo;
    mySingleArtifact = items.size() == 1;
    createContent(editScript, items);
    adjustWholePanel();
  }

  private void createContent(List<? extends EditPrimitive> editScript, List<? extends ItemWrapper> items)
    throws CantPerformExceptionExplained
  {
    int row = 0;
    boolean stretchesY = false;

    final GridBagConstraints gbc = new GridBagConstraints(
      0, 0, 1, 1, 0d, 0d,
      GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
      AwtUtil.EMPTY_INSETS, 0, 0);

    PropertyMap additionalProperties = new PropertyMap();
    for (final EditPrimitive primitive : editScript) {
      if (primitive instanceof EditPrimitive.PseudoEditPrimitive) {
        row = addPseudoPrimitive(row, gbc, primitive);
        continue;
      }

      final Pair<JComponent, Boolean> pair = primitive.createEditor(myLife, myModifiable, myMetaInfo, items, additionalProperties);
      if (pair == null || pair.getFirst() == null) {
        myComponents.put(primitive, null);
        continue;
      }

      final JComponent component = pair.getFirst();
      @Nullable final Boolean initiallyEnabled = pair.getSecond();

      myComponents.put(primitive, component);

      if (myInitialFocusOwner == null) {
        myInitialFocusOwner = primitive.getInitialFocusOwner(component);
      }

      final boolean inline = primitive.isInlineLabel();
      @Nullable ValueModel<Boolean> enabledModel = additionalProperties.get(EditPrimitive.ENABLED_MODEL);
      final JComponent fieldTitle = createTitleComponent(primitive, component, initiallyEnabled, inline, enabledModel);
      row = prepareTitleConstraints(row, gbc, inline);
      myPanel.add(fieldTitle, gbc);

      final boolean enabled = mySingleArtifact || initiallyEnabled == null || initiallyEnabled;
      primitive.enablePrimitive(component, enabled);
      enablePrimitiveByModel(enabledModel, primitive, component);

      stretchesY |= prepareFieldConstraints(row, gbc, primitive, inline);
      myPanel.add(component, gbc);
      row++;
    }

    if (!stretchesY) {
      addVerticalFill(row, gbc);
    }
  }

  private int addPseudoPrimitive(int row, GridBagConstraints gbc, EditPrimitive primitive) {
    if (primitive == EditPrimitive.SPACE || primitive instanceof EditPrimitive.Separator) {
      gbc.gridx = 0;
      gbc.gridy = row++;
      gbc.weightx = 0;
      gbc.weighty = 0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.insets = EMPTY_INSETS;
      gbc.gridwidth = 2;

      if (primitive == EditPrimitive.SPACE) {
        myPanel.add(Box.createVerticalStrut(VERTICAL_GAP), gbc);
      } else {
        if(row != 1) {
          gbc.insets = SEPARATOR_GAP;
        }
        myPanel.add(new ASeparator(primitive.toString()), gbc);
      }
    }
    return row;
  }

  private JComponent createTitleComponent(
      EditPrimitive primitive, JComponent component, Boolean initiallyEnabled, boolean inline, @Nullable ValueModel<Boolean> enabledModel)
    throws CantPerformExceptionExplained
  {
    final NameMnemonic fieldName = primitive.getLabel(myMetaInfo);
    if (mySingleArtifact) {
      return createLabelTitle(component, fieldName);
    } else if (initiallyEnabled != null) {
      return createCheckboxTitle(primitive, component, fieldName, initiallyEnabled, enabledModel);
    } else if (inline) {
      return createPseudoCheckBoxTitle(component, fieldName);
    } else {
      return createLabelTitle(component, fieldName);
    }
  }

  private int prepareTitleConstraints(int row, GridBagConstraints gbc, boolean inline) {
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = EMPTY_INSETS;

    if (inline) {
      gbc.gridwidth = 1;
      gbc.insets = getLabelInsets(row == 0);
    } else {
      gbc.gridwidth = 2;
      gbc.insets = getComponentInsets(row == 0);
      row++;
    }

    return row;
  }

  private Insets getLabelInsets(boolean firstLine) {
    return firstLine ? TOP_LABEL_INSETS : NOT_TOP_LABEL_INSETS;
  }

  private Insets getComponentInsets(boolean firstLine) {
    return firstLine ? EMPTY_INSETS : TOP_GAP;
  }

  private boolean prepareFieldConstraints(int row, GridBagConstraints gbc, EditPrimitive primitive, boolean inline) {
    gbc.weightx = 1;
    gbc.insets = getComponentInsets(row == 0);
    gbc.fill = GridBagConstraints.BOTH;

    if (inline) {
      gbc.gridx = 1;
      gbc.gridwidth = 1;
    } else {
      gbc.gridx = 0;
      gbc.gridy = row;
      gbc.gridwidth = 2;
    }

    gbc.weighty = primitive.getEditorWeightY();
    return gbc.weighty > 0;
  }

  private void addVerticalFill(int row, GridBagConstraints gbc) {
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.weightx = 0;
    gbc.weighty = 1;
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.insets = EMPTY_INSETS;
    gbc.gridwidth = 2;
    myPanel.add(Box.createVerticalStrut(0), gbc);
  }

  private void enablePrimitiveByModel(@Nullable final ValueModel<Boolean> enabledModel, final EditPrimitive primitive, final JComponent component) {
    if (enabledModel != null) {
      ChangeListener listener = new ChangeListener() {
        @Override
        public void onChange() {
          primitive.enablePrimitive(component, enabledModel.getValue() == Boolean.TRUE);
        }
      };
      listener.onChange();
      enabledModel.addAWTChangeListener(myLife, listener);
    }
  }

  private void adjustWholePanel() {
    Aqua.cleanScrollPaneBorder(myScrollpane);
    Aero.cleanScrollPaneBorder(myScrollpane);
    Aqua.cleanScrollPaneResizeCorner(myScrollpane);
    myPanel.setBorder(UIUtil.EDITOR_PANEL_BORDER);
    myPanel.setAlignmentY(0F);
    UIUtil.setDefaultLabelAlignment(myPanel);
    Aqua.disableMnemonics(myPanel);
    new DocumentFormAugmentor().augmentForm(myLife, myPanel, true);
  }

  @Override
  public Modifiable getModifiable() {
    return myModifiable;
  }

  private JLabel createLabelTitle(JComponent component, NameMnemonic fieldName) {
    JLabel c = new JLabel();
    if (fieldName != null)
      fieldName.setToLabel(c);
    c.setLabelFor(component);
    return c;
  }

  private JComponent createCheckboxTitle(
      final EditPrimitive primitive, final JComponent component,
      NameMnemonic fieldName, boolean initiallySelected, @Nullable ValueModel<Boolean> enabledModel)
  {
    final JLabel label = createLabelTitle(component, fieldName);

    final JCheckBox check = new JCheckBox("");
    check.setSelected(initiallySelected);
    myCheckBoxes.put(primitive, check);

    final class MyListener extends MouseAdapter implements ItemListener {
      @Override
      public void mouseClicked(MouseEvent e) {
        check.requestFocusInWindow();
        check.setSelected(!check.isSelected());
      }

      @Override
      public void itemStateChanged(ItemEvent e) {
        primitive.enablePrimitive(component, check.isSelected());
        myModifiable.fireChanged();
      }
    }

    final MyListener myListener = new MyListener();
    label.addMouseListener(myListener);
    check.addItemListener(myListener);

    enableCheckboxByModel(initiallySelected, enabledModel, check);

    return combine(check, label);
  }

  private void enableCheckboxByModel(final boolean initiallySelected, final ValueModel<Boolean> enabledModel, final JCheckBox check) {
    if (enabledModel != null) {
      ChangeListener listener = new ChangeListener() {
        boolean myLastSelected; {
          myLastSelected = initiallySelected;
        }

        @Override
        public void onChange() {
          Boolean enabled = enabledModel.getValue();
          check.setEnabled(enabled);
          if (enabled != Boolean.TRUE) {
            myLastSelected = check.isSelected();
            check.setSelected(false);
          } else {
            check.setSelected(myLastSelected);
          }
        }
      };
      listener.onChange();
      enabledModel.addAWTChangeListener(myLife, listener);
    }
  }

  private JComponent combine(JComponent west, JComponent center) {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(west, BorderLayout.WEST);
    panel.add(center, BorderLayout.CENTER);
    return panel;
  }

  private JComponent createPseudoCheckBoxTitle(JComponent component, NameMnemonic fieldName) {
    final JLabel label = createLabelTitle(component, fieldName);

    final JCheckBox check = new JCheckBox("");
    check.setSelected(true);
    check.setEnabled(false);

    return combine(check, label);
  }

  @Override
  public String getSaveProblem() {
    for (Map.Entry<EditPrimitive, JComponent> entry : myComponents.entrySet()) {
      JComponent component = entry.getValue();
      if (component == null)
        continue;
      EditPrimitive primitive = entry.getKey();
      JCheckBox checkbox = myCheckBoxes.get(primitive);
      if (checkbox == null || checkbox.isSelected()) {
        String problem = primitive.getSaveProblem(component, myMetaInfo);
        if (problem != null) {
          return problem;
        }
      }
    }
    return null;
  }

  @Override
  public void copyValues(ItemUiModel model) throws CantPerformExceptionExplained {
    for (Map.Entry<EditPrimitive, JComponent> entry : myComponents.entrySet()) {
      EditPrimitive primitive = entry.getKey();
      JComponent component = entry.getValue();
      JCheckBox checkbox = myCheckBoxes.get(primitive);
      if (checkbox == null || checkbox.isSelected()) {
        primitive.setValue(model, component);
      }
    }
    if (model.isChanged()) {
      copyAdditionalValues(model);
    }
  }

  protected void copyAdditionalValues(ItemUiModel model) {
  }

  @Override
  public boolean isConsiderablyModified() {
    for (Map.Entry<EditPrimitive, JComponent> entry : myComponents.entrySet()) {
      EditPrimitive primitive = entry.getKey();
      JCheckBox checkbox = myCheckBoxes.get(primitive);
      if (checkbox == null || checkbox.isSelected()) {
        if (primitive.isConsiderablyModified(entry.getValue())) {
          return true;
        }
      }
    }
    return false;
  }

  @Deprecated
  @Override
  public void dispose() {
    myLife.detach();
    myModifiable.dispose();
  }

  @Override
  public Detach getDetach() {
    return new Disposer(this);
  }

  @Override
  public JComponent getComponent() {
    return myScrollpane;
  }

  @Nullable
  public JComponent getInitialFocusOwner() {
    return myInitialFocusOwner;
  }
}
