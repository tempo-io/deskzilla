package com.almworks.spi.provider.wizard;

import com.almworks.gui.WizardPage;
import com.almworks.util.components.ALabel;
import com.almworks.util.ui.ColorUtil;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.AnAction;
import org.almworks.util.detach.Lifecycle;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;

/**
 * Boilerplate base wizard page.
 */
public abstract class BasePage implements WizardPage {
  protected final String myPageID;
  protected volatile String myPrevPageID;
  protected volatile String myNextPageID;

  protected final JPanel myPanel;
  protected AnAction myMoreAction;

  protected final Lifecycle myLifecycle = new Lifecycle();

  protected BasePage(String title, String pageID, String prevID, String nextID) {
    this(title, pageID);
    myPrevPageID = prevID;
    myNextPageID = nextID;
  }

  protected BasePage(String title, String pageID) {
    myPageID = pageID;
    myPanel = createWholePanel(title);
  }

  private JPanel createWholePanel(String title) {
    final JPanel panel = new JPanel(new BorderLayout(0, 15));
    panel.setBorder(UIUtil.EDITOR_PANEL_BORDER);
    panel.add(createHeaderLabel(title), BorderLayout.NORTH);
    panel.add(createContent(), BorderLayout.CENTER);
    return panel;
  }

  private ALabel createHeaderLabel(String title) {
    final ALabel header = new ALabel(title);
    UIUtil.adjustFont(header, 1.4F, Font.BOLD, true);
    header.setForeground(ColorUtil.between(header.getForeground(), Color.BLUE, 0.3F));
    return header;
  }

  protected abstract JComponent createContent();

  @Override
  public JComponent getContent() {
    return myPanel;
  }

  @Override
  public final String getPageID() {
    return myPageID;
  }

  @Override  @NotNull
  public String getPrevPageID() {
    return myPrevPageID;
  }

  @Override @NotNull
  public String getNextPageID() {
    return myNextPageID;
  }

  @Override
  public AnAction[] getMoreActions() {
    return myMoreAction == null ? null : new AnAction[] { myMoreAction };
  }

  @Override
  public void aboutToDisplayPage(String prevPageID) {}

  @Override
  public void displayingPage(String prevPageID) {}

  @Override
  public void aboutToHidePage(String nextPageID) {
    myLifecycle.cycle();
  }

  @Override
  public void backInvoked() {}

  @Override
  public void nextInvoked() {}
}
