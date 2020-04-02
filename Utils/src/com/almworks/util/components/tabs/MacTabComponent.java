package com.almworks.util.components.tabs;

import com.almworks.util.images.*;
import com.almworks.util.ui.MegaMouseAdapter;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.swing.AwtUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Component for {@link ContentTab} headers on Mac OS X.
 */
public class MacTabComponent extends JComponent implements ActionListener {
  private static final Icon EMPTY_ICON = new EmptyIcon(Icons.MAC_TAB_CLOSE_INACTIVE);
  private static final Icon INACTIVE_ICON = new AlphaIcon(Icons.MAC_TAB_CLOSE_INACTIVE, 0.75f);
  private static final Icon ACTIVE_ICON = Icons.MAC_TAB_CLOSE_ACTIVE;

  private final ContentTab myTab;
  private final JLabel myLeft = new JLabel(EMPTY_ICON);
  private final JLabel myCenter = new JLabel();
  private final JLabel myRight = new JLabel(EMPTY_ICON);

  private boolean myOverTab = false;
  private boolean myOverIcon = false;

  private final Timer myTimer = new Timer(125, this);
  private String myTabName;

  public MacTabComponent(ContentTab tab) {
    myTab = tab;
    setLayout(new BorderLayout(5, 0));
    add(myLeft, BorderLayout.WEST);
    add(myCenter, BorderLayout.CENTER);
    add(myRight, BorderLayout.EAST);
    myCenter.setHorizontalAlignment(SwingConstants.CENTER);
    updateName();

    myTimer.setRepeats(false);

    addMouseListener(new MegaMouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        myOverTab = true;
        updateIcon();
      }
      @Override
      public void mouseExited(MouseEvent e) {
        myOverTab = false;
        updateIcon();
      }
      @Override
      protected void onMouseEvent(MouseEvent e) {
        if(e.getID() == MouseEvent.MOUSE_PRESSED) {
          myTab.select();
        }
        if(UIUtil.isPrimaryDoubleClick(e)) {
          myTab.toggleExpand(MacTabComponent.this);
        }
        if(e.isPopupTrigger()) {
          myTab.showPopup(e);
        }
      }
    });

    myLeft.addMouseListener(new MegaMouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        myOverIcon = true;
        updateIcon();
      }
      @Override
      public void mouseExited(MouseEvent e) {
        myOverIcon = false;
        updateIcon();
      }
      @Override
      protected void onMouseEvent(MouseEvent e) {
        if(e.getID() == MouseEvent.MOUSE_RELEASED && e.getButton() == MouseEvent.BUTTON1) {
          myTab.delete();
        }
      }
    });
  }

  private void updateName() {
    final String tabName = myTab.getName();
    if(!tabName.equals(myTabName)) {
      myTabName = tabName;
      myCenter.setText(myTabName);
      myLeft.setToolTipText("Close \"" + myTabName + "\"");
    }
  }

  private void updateIcon() {
    myTimer.restart();
  }

  public void actionPerformed(ActionEvent e) {
    if(e.getSource() == myTimer) {
      if(myOverIcon) {
        myLeft.setIcon(ACTIVE_ICON);
      } else if(myOverTab) {
        myLeft.setIcon(INACTIVE_ICON);
      } else {
        myLeft.setIcon(EMPTY_ICON);
      }
    }
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    String tooltip = myTab.getTooltip();
    if(tooltip == null) {
      tooltip = myTab.getName();
    }
    return tooltip.trim().length() == 0 ? null : tooltip;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    ToolTipManager.sharedInstance().registerComponent(this);
  }

  @Override
  public void removeNotify() {
    ToolTipManager.sharedInstance().unregisterComponent(this);
    super.removeNotify();
  }

  @Override
  public void paint(Graphics g) {
    AwtUtil.applyRenderingHints(g);
    updateName();
    super.paint(g);
  }
}
