package com.almworks.bugzilla.provider;

import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.SelectionInListModel;
import com.almworks.util.components.AComboBox;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.config.Configuration;
import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.io.IOUtils;
import com.almworks.util.ui.ComponentEnabler;
import com.almworks.util.ui.UIUtil;

import javax.swing.*;
import java.util.TimeZone;

/**
 * GUI form for the "Bugzilla" section of the "Advanced" dialog
 * for the URL and credentials page of the Bugzilla connection wizard.
 */
public class BugzillaConnectionAdvancedForm {
  private JPanel myWholePanel;
  JCheckBox mySuffixCheck;
  JTextField mySuffixField;
  JCheckBox myCharsetCheck;
  JComboBox myCharsetCombo;
  AComboBox<TimeZone> myTimezoneCombo;
  private JLabel myTimezoneLabel;

  public BugzillaConnectionAdvancedForm() {
    adjustToPlatform();
    initialize();
  }

  private void adjustToPlatform() {
    UIUtil.setDefaultLabelAlignment(myWholePanel);
    Aqua.disableMnemonics(myWholePanel);
  }

  private void initialize() {
    ComponentEnabler.create(mySuffixCheck, mySuffixField).setDefaultComponent(mySuffixField);
    ComponentEnabler.create(myCharsetCheck, myCharsetCombo).setDefaultComponent(myCharsetCombo);
    myTimezoneLabel.setLabelFor(myTimezoneCombo);
  }

  public void loadFromConfiguration(ReadonlyConfiguration config) {
    final OurConfiguration ourConfig = new OurConfiguration(config);
    loadEmailSuffix(ourConfig);
    loadCharset(ourConfig);
    loadTimezone(ourConfig);
  }

  private void loadEmailSuffix(OurConfiguration config) {
    mySuffixCheck.setSelected(config.isUsingEmailSuffix());
    mySuffixField.setText(config.getEmailSuffix());
    if (mySuffixField.getText().isEmpty()) mySuffixField.setText("@");
  }

  private void loadCharset(OurConfiguration config) {
    myCharsetCheck.setSelected(config.isCharsetSpecified());
    myCharsetCombo.setModel(new DefaultComboBoxModel(IOUtils.getAvalaibleCharsetNames()));
    myCharsetCombo.setSelectedItem(config.getCharset());
  }

  private void loadTimezone(OurConfiguration config) {
    final AListModel<TimeZone> zones = UIUtil.createAvailableTimezonesModel();
    final TimeZone selected = config.getTimeZone();
    final SelectionInListModel<TimeZone> comboModel = SelectionInListModel.createForever(zones, selected);
    myTimezoneCombo.setModel(comboModel);
    myTimezoneCombo.setCanvasRenderer(UIUtil.TIMEZONE_RENDERER);
  }

  public void saveToConfiguration(Configuration config) {
    saveEmailSuffix(config);
    saveCharset(config);
    saveTimezone(config);
  }

  private void saveEmailSuffix(Configuration config) {
    if(mySuffixCheck.isSelected()) {
      config.setSetting(OurConfiguration.IS_USING_EMAIL_SUFFIX, true);
      config.setSetting(OurConfiguration.EMAIL_SUFFIX, mySuffixField.getText());
    } else {
      config.removeSettings(OurConfiguration.IS_USING_EMAIL_SUFFIX);
      config.removeSettings(OurConfiguration.EMAIL_SUFFIX);
    }
  }

  private void saveCharset(Configuration config) {
    if(myCharsetCheck.isSelected()) {
      config.setSetting(OurConfiguration.IS_CHARSET_SPECIFIED, true);
    } else {
      config.removeSettings(OurConfiguration.IS_CHARSET_SPECIFIED);
    }
    config.setSetting(OurConfiguration.CHARSET, String.valueOf(myCharsetCombo.getSelectedItem()));
  }

  private void saveTimezone(Configuration config) {
    final TimeZone timeZone = myTimezoneCombo.getModel().getSelectedItem();
    if(timeZone != null) {
      config.setSetting(OurConfiguration.TIMEZONE, timeZone.getID());
    }
  }

  public JComponent getComponent() {
    return myWholePanel;
  }
}
