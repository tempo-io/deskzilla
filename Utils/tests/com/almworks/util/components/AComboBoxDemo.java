package com.almworks.util.components;

import com.almworks.util.advmodel.FixedListModel;
import com.almworks.util.advmodel.SelectionInListModel;
import com.almworks.util.components.plaf.LAFUtil;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.debug.DebugFrame;
import com.almworks.util.ui.UIUtil;
import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;

public class AComboBoxDemo implements Runnable {
  private static final String[] ITEMS = new String[] {"odnazhdi v stu", "denuju zimnuu poru", "sizhu za reshetkoy", "v temnitse siroy"};

  public static void main(String[] args) {
    LAFUtil.initializeLookAndFeel();
    SwingUtilities.invokeLater(new AComboBoxDemo());
  }

  public void run() {
    JComboBox jcombo = new JComboBox(ITEMS);
    AComboBox<String> acombo = new AComboBox<String>();
    acombo.setCanvasRenderer(new CanvasRenderer<String>() {
      public void renderStateOn(CellState state, Canvas canvas, String item) {
        canvas.appendText("Renderered: " + item);
      }
    });
    acombo.setModel(SelectionInListModel.create(Lifespan.FOREVER, FixedListModel.create(ITEMS), null));

    JComboBox ejcombo = new JComboBox(ITEMS);
    AComboBox<String> eacombo = new AComboBox<String>();
    eacombo.setModel(SelectionInListModel.create(Lifespan.FOREVER, FixedListModel.create(ITEMS), null));
    ejcombo.setEditable(true);
    eacombo.setEditable(true);

    DefaultFormBuilder builder = new DefaultFormBuilder(new FormLayout("left:d, 4dlu, d:g"));
    builder.append("&Text Field:", new JTextField());
    builder.append("&JComboBox:", jcombo);
    builder.append("&AComboBox:", acombo);
    builder.append("&Editable JComboBox:", ejcombo);
    builder.append("E&ditable AComboBox:", eacombo);
    builder.append(new JLabel("J: " + jcombo.getPreferredSize() + "; A: " + acombo.getPreferredSize() + "; EJ: " + ejcombo.getPreferredSize() + "; EA: " + eacombo.getPreferredSize()), 3);
    builder.setBorder(UIUtil.BORDER_5);

    DebugFrame.show(builder.getPanel(), 500, 300);
  }
}
