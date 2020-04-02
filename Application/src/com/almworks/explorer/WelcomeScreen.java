package com.almworks.explorer;

import com.almworks.api.platform.ProductInformation;
import com.almworks.util.Terms;
import com.almworks.util.components.ALabel;
import com.almworks.util.components.ScrollablePanel;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.datetime.DateUtil;
import com.almworks.util.i18n.Local;
import com.almworks.util.ui.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

class WelcomeScreen {
  private final ProductInformation myProductInfo;
  private final boolean myCoverpage;
  private JComponent myWelcomePanel;

  public WelcomeScreen(ProductInformation productInfo, boolean coverpage)
  {
    myProductInfo = productInfo;
    myCoverpage = coverpage;
    init();
  }

  private void init() {
    Box welcomeContent = Box.createVerticalBox();
    welcomeContent.setBorder(new EmptyBorder(0, 9, 0, 9));


    Color bg = ColorUtil.between(UIManager.getColor("EditorPane.background"), GlobalColors.CORPORATE_COLOR_1, 0.15F);

    //WelcomePanel panel = new WelcomePanel(welcomeContent, 0, 0, bg);
    JPanel panel = new JPanel(new BorderLayout(0, 15));
    panel.setOpaque(true);
    panel.setBackground(bg);
    panel.setBorder(new EmptyBorder(31, 51, 31, 51));
    panel.add(SingleChildLayout.envelop(welcomeContent, 0F, 0F), BorderLayout.CENTER);
    if (myCoverpage) {
      panel.add(createTopComponent(bg), BorderLayout.NORTH);
      panel.add(createBottomComponent(bg), BorderLayout.SOUTH);
    }
    myWelcomePanel = new JScrollPane(new ScrollablePanel(panel));
    Aqua.cleanScrollPaneBorder(myWelcomePanel);
  }

  private Component createBottomComponent(Color bg) {
    StringBuffer footer = new StringBuffer();
    footer.append(myProductInfo.getName());
    footer.append(" ").append(myProductInfo.getVersion());
    footer.append(", build ").append(myProductInfo.getBuildNumber().toDisplayableString());
    footer.append(" of ").append(DateUtil.US_MEDIUM.format(myProductInfo.getBuildDate()));
    ALabel label = new ALabel(footer.toString());
    label.setHorizontalAlignment(SwingConstants.TRAILING);
    label.setBorder(UIUtil.createNorthBevel(bg));

    return label;
  }

//  private Component createTemporaryLaunchWarning() {
//    JPanel panel = new JPanel(new InlineLayout(InlineLayout.HORISONTAL, 29));
//    panel.setOpaque(false);
//    panel.add(new JLabel("<html><b>WARNING:</b> A license key will be required next time " +
//      Local.text(Terms.key_Deskzilla) + " is started."));
//    panel.add(new URLLink(Setup.getUrlGetLicense_Eval(), true, "Get Free Evaluation License Key..."));
//    AnAction action = myActionRegistry.getAction(MainMenu.Help.SELECT_LICENSE);
//    if (action != null) {
//      Link link = new Link();
//      link.setAnAction(action);
//      panel.add(link);
//    }
//    return panel;
//  }

  private Component createTopComponent(Color bg) {
    ALabel label = new ALabel(Local.parse("Welcome to " + Terms.ref_Deskzilla));
    UIUtil.adjustFont(label, 2.05F, -1, true);
    label.setBorder(UIUtil.createSouthBevel(bg));
    label.setForeground(bg.darker().darker());
    label.setHorizontalAlignment(SwingConstants.TRAILING);
    return label;
  }

  public void reset() {
  }

  public JComponent getComponent() {
    return myWelcomePanel;
  }
}
