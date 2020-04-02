package com.almworks.bugzilla.gui;

import com.jgoodies.forms.layout.*;

import javax.swing.*;
import javax.swing.text.JTextComponent;

/**
 * Builds fields panel for New/Edit Bug dialog.
 * Field components are laid out two or one in a row.
 */
class FieldsPanelBuilder {
  static enum Width {
    FORCE_FULL_ROW,
    MAY_BE_HALF_ROW,
    DONT_CARE
  }

  private final JPanel myPanel;
  private final FormLayout myLayout;

  private boolean myLeft = true;

  public FieldsPanelBuilder(JPanel fieldsPanel) {
    myPanel = fieldsPanel;
    assert myPanel.getLayout() instanceof FormLayout;
    myLayout = (FormLayout) myPanel.getLayout();
  }

  public void addField(JComponent component, JLabel label) {
    addField(component, label, Width.DONT_CARE);
  }

  public void addField(JComponent component, JLabel label, Width width) {
    final boolean fullRow = width == Width.FORCE_FULL_ROW ||
      component instanceof JTextComponent || component instanceof JScrollPane;
    final boolean preferHalfRow = width == Width.MAY_BE_HALF_ROW && !myLeft;
    if (fullRow && !preferHalfRow) {
      addFullRowField(label, component);
    } else {
      addHalfRowField(label, component);
    }
  }

  private void addFullRowField(JLabel caption, JComponent editor) {
    final int rows = editor instanceof JScrollPane ? 4 : 1;

    myLayout.appendRow(new RowSpec("4dlu"));

    int gridY = myLayout.getRowCount() + 1;
    for (int i = 0; i < rows; i++) {
      myLayout.appendRow(new RowSpec("d"));
      myLayout.addGroupedRow(myLayout.getRowCount());
    }

    CellConstraints cc =
      new CellConstraints(1, gridY, CellConstraints.FILL, rows > 1 ? CellConstraints.FILL : CellConstraints.CENTER);
    myPanel.add(caption, cc);
    cc.gridHeight = rows;
    cc.gridX += 2;
    cc.gridWidth = 5;
    myPanel.add(editor, cc);
    myLeft = true;
  }

  private void addHalfRowField(JLabel caption, JComponent editor) {
    if (myLeft) {
      // insert new rows
      myLayout.appendRow(new RowSpec("4dlu"));
      myLayout.appendRow(new RowSpec("d"));
      myLayout.addGroupedRow(myLayout.getRowCount());
    }
    CellConstraints cc =
      new CellConstraints(myLeft ? 1 : 5, myLayout.getRowCount(), CellConstraints.FILL, CellConstraints.CENTER);
    myPanel.add(caption, cc);
    cc.gridX += 2;
    myPanel.add(editor, cc);
    myLeft = !myLeft;
  }
}