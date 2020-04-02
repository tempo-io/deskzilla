package com.almworks.api.database.auth;

import javax.swing.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface UIDataBuilder <T> {
  JComponent getDialogComponent();

  void reset(T initialData);

  void reset();

  T getData();

  boolean show();
}
