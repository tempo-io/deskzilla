package com.almworks.bugzilla.provider;

import com.almworks.spi.provider.util.PasswordUtil;
import com.almworks.spi.provider.wizard.BaseUrlAndCredentialsForm;
import com.almworks.util.components.ALabelWithExplanation;
import com.almworks.util.components.completion.CompletingComboBox;
import com.almworks.util.config.ReadonlyConfiguration;

import javax.swing.*;

/**
 * GUI form for the New Connection wizard's URL and credentials page.
 */
public class BugzillaConnectionUrlForm extends BaseUrlAndCredentialsForm {
  private JPanel myWholePanel;
  private CompletingComboBox myUrl;
  private JLabel myUsernameLabel;
  private JTextField myUsername;
  private JLabel myPasswordLabel;
  private JPasswordField myPassword;
  private JCheckBox myAnonymous;
  private JPanel myMessagePlace;

  private final ALabelWithExplanation myInfo = new ALabelWithExplanation();
  private final ALabelWithExplanation myError = new ALabelWithExplanation();

  @Override
  public void loadValuesFromConfiguration(ReadonlyConfiguration config) {
    myUrl.setSelectedItem(config.getSetting(OurConfiguration.BASE_URL, ""));
    myAnonymous.setSelected(config.getBooleanSetting(OurConfiguration.IS_ANONYMOUS_ACCESS, false));
    myUsername.setText(config.getSetting(OurConfiguration.USER_NAME, ""));
    myPassword.setText(PasswordUtil.getPassword(config));
  }

  @Override
  protected JPanel getWholePanel() {
    return myWholePanel;
  }

  @Override
  protected CompletingComboBox getUrlField() {
    return myUrl;
  }

  @Override
  protected JTextField getUsernameField() {
    return myUsername;
  }

  @Override
  protected JPasswordField getPasswordField() {
    return myPassword;
  }

  @Override
  protected JCheckBox getAnonymousCheckBox() {
    return myAnonymous;
  }

  @Override
  protected JPanel getMessagePlace() {
    return myMessagePlace;
  }

  @Override
  protected ALabelWithExplanation getErrorLabel() {
    return myError;
  }

  @Override
  protected ALabelWithExplanation getInfoLabel() {
    return myInfo;
  }
}