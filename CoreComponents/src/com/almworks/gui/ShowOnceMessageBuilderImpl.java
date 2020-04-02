package com.almworks.gui;

import com.almworks.api.gui.DialogBuilder;
import com.almworks.api.gui.ShowOnceMessageBuilder;
import com.almworks.util.L;
import com.almworks.util.config.Configuration;
import org.almworks.util.Failure;
import org.almworks.util.detach.Detach;

import javax.swing.*;

/**
 * @author dyoma
 */
public class ShowOnceMessageBuilderImpl implements ShowOnceMessageBuilder {
  private static final String CANCELED_EARLIER = "canceledEarlier";
  private final DialogBuilder myDialog;
  private final Configuration myConfig;
  private final JCheckBox myDontShowAgain = new JCheckBox(L.checkbox("Don't show this message in the future"));

  public ShowOnceMessageBuilderImpl(DialogBuilder dialogBuilder, Configuration config) {
    myDialog = dialogBuilder;
    myConfig = config;
    myDialog.setBottomLineComponent(myDontShowAgain);
    myDialog.setEmptyOkAction();
    myDialog.setBottomBevel(false);
  }

  public void setTitle(String title) {
    myDialog.setTitle(title);
  }

  public void setContent(JComponent component) {
    myDialog.setContent(component);
  }

  public void setMessage(String message, int type) {
    Icon icon;
    switch(type) {
    case JOptionPane.PLAIN_MESSAGE: icon = null; break;
    case JOptionPane.ERROR_MESSAGE: icon = UIManager.getIcon("OptionPane.errorIcon");  break;
    case JOptionPane.INFORMATION_MESSAGE: icon = UIManager.getIcon("OptionPane.informationIcon"); break;
    case JOptionPane.WARNING_MESSAGE: icon = UIManager.getIcon("OptionPane.warningIcon"); break;
    case JOptionPane.QUESTION_MESSAGE: icon = UIManager.getIcon("OptionPane.questionIcon"); break;
    default: throw new Failure(String.valueOf(type));
    }
    setContent(new JLabel(message, icon, SwingConstants.LEADING));
  }

  public void setMessage(String message) {
    setMessage(message, JOptionPane.PLAIN_MESSAGE);
  }

  public void showMessage() {
    if (myConfig.getBooleanSetting(CANCELED_EARLIER, false))
      return;
    myDialog.setModal(true);
    myDialog.showWindow(new Detach() {
      protected void doDetach() {
        myConfig.setSetting(CANCELED_EARLIER, myDontShowAgain.isSelected());
      }
    });
  }
}
