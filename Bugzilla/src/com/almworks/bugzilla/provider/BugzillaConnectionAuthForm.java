package com.almworks.bugzilla.provider;

import com.almworks.util.components.ALabel;
import com.almworks.util.components.ARadioWithExplanation;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.ui.InlineLayout;
import com.almworks.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

class BugzillaConnectionAuthForm {
  private static final int RADIO_GAP = new JRadioButton("").getPreferredSize().width;
  private static final int LINE_GAP = new JLabel("Foo").getPreferredSize().height / 2;

  final JPanel myWholePanel = new JPanel();
  final JLabel myUsernameLabel = new JLabel();
  final JTextField myDomainField = new JTextField();
  JRadioButton myExternalOption;
  JRadioButton mySuffixOption;
  JRadioButton myNeitherOption;

  private final ButtonGroup myRadioGroup = new ButtonGroup();
  private final UIUtil.Positioner myPositioner = new UIUtil.IndependentPositioner(
    new UIUtil.Positioner1D() {
      @Override
      public int getPosition(int ss, int sl, int os, int ol, int cl) {
        return UIUtil.ALIGN_START.getPosition(ss, sl, os + RADIO_GAP, ol - RADIO_GAP, cl);
      }
    },
    UIUtil.NOTICE_POPUP_Y);

  BugzillaConnectionAuthForm() {
    setupComponents();
  }

  private void setupComponents() {
    setupWholePanel(
      createExplanation(), lineGap(),
      createExtAuthRow(), lineGap(),
      createSuffixRow(), createEmailRow(), lineGap(),
      createNeitherRow());
  }

  private void setupWholePanel(Component... rows) {
    myWholePanel.setLayout(InlineLayout.vertical(0));
    for(final Component row : rows) {
      myWholePanel.add(row);
    }
    Aqua.disableMnemonics(myWholePanel);
  }

  private JComponent lineGap() {
    return (JComponent)Box.createVerticalStrut(LINE_GAP);
  }

  private JComponent createExplanation() {
    final ALabel label = new ALabel();
    label.setText(
      "<html>Please select an option below that best describes your Bugzilla. " +
      "<br><b>If in doubt, please contact your Bugzilla administrator.</b>");
    return label;
  }

  private JComponent createExtAuthRow() {
    final ARadioWithExplanation rwe = new ARadioWithExplanation();
    rwe.setTextAndExplanation(
      "This Bugzilla uses LDAP or RADIUS authentication",
      "<html>LDAP and RADIUS are protocols that Bugzilla can use to access an external authentication service, " +
      "e.g. your corporate directory. " +
      "Deskzilla doesn't fully support external authentication yet, but your connection should be usable.");
    rwe.setPositioner(myPositioner);
    myExternalOption = rwe.getMain();
    myExternalOption.setSelected(true);
    myRadioGroup.add(myExternalOption);
    return rwe;
  }

  private JComponent createSuffixRow() {
    final ARadioWithExplanation rwe = new ARadioWithExplanation();
    rwe.setTextAndExplanation(
      "This Bugzilla uses an e-mail suffix",
      "<html>E-mail suffix is a string that Bugzilla appends to login names to obtain " +
      "users' e-mail addresses. " +
      "If a suffix is used, please provide the full e-mail address " +
      "that you use to receive notifications from this Bugzilla.");
    rwe.setPositioner(myPositioner);
    mySuffixOption = rwe.getMain();
    myRadioGroup.add(mySuffixOption);
    mySuffixOption.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        final boolean selected = mySuffixOption.isSelected();
        myUsernameLabel.setEnabled(selected);
        myDomainField.setEnabled(selected);
        if(selected) {
          myDomainField.requestFocusInWindow();
        }
      }
    });
    return rwe;
  }

  private JComponent createEmailRow() {
    final Component gap = Box.createHorizontalStrut(RADIO_GAP);
    final JLabel label = new JLabel("Your e-mail:  ");
    UIUtil.adjustFont(myUsernameLabel, 0, Font.BOLD);
    myDomainField.setColumns(20);
    return createRow(gap, label, myUsernameLabel, myDomainField);
  }

  private JComponent createRow(Component... cells) {
    final JPanel row = new JPanel(InlineLayout.horizontal(0));
    for(final Component cell : cells) {
      row.add(cell);
    }
    return row;
  }

  private JComponent createNeitherRow() {
    final ARadioWithExplanation rwe = new ARadioWithExplanation();
    rwe.setTextAndExplanation(
      "Neither of the above",
      "<html>If you're absolutely sure that neither of the above is true, choose this option. " +
      "Also, if you cannot decide which option to pick, you can try this one first.");
    rwe.setPositioner(myPositioner);
    myNeitherOption = rwe.getMain();
    myRadioGroup.add(myNeitherOption);
    return rwe;
  }
}
