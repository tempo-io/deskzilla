package com.almworks.api.gui;

import javax.swing.*;

/**
 * @author dyoma
 */
public interface ShowOnceMessageBuilder {
  void setTitle(String title);

  void setContent(JComponent component);

  void setMessage(String message);

  void showMessage();

  void setMessage(String message, int type);
}
