package com.almworks.api.gui;

import com.almworks.api.container.MutableComponentContainer;
import com.almworks.util.config.Configuration;
import com.almworks.util.properties.Role;
import com.almworks.util.ui.UIComponentWrapper;
import com.almworks.util.ui.WindowUtil;
import com.almworks.util.ui.actions.AnActionListener;
import com.almworks.util.ui.actions.DataProvider;
import org.almworks.util.detach.Detach;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;

/**
 * @author : Dyoma
 */
public interface BasicWindowBuilder<C extends UIComponentWrapper> {
  void setTitle(String title);

  void showWindow();

  WindowController showWindow(Detach disposeNotification);

  void setPreferredSize(Dimension size);

  MutableComponentContainer getWindowContainer();

  Configuration getConfiguration();

  void setContent(C content);

  void setContentClass(Class<? extends C> contentClass);

  Role<? extends C> getContentRole();

  void setInitialFocusOwner(Component component);

  void detachOnDispose(Detach detach);

  void setCloseConfirmation(@NotNull AnActionListener confirmation);

  void addProvider(DataProvider provider);

  void setIgnoreStoredSize(boolean ignore);

  void setContent(JComponent content);

  @Nullable
  Component getInitialFocusOwner();

  void setWindowPositioner(WindowUtil.WindowPositioner adjuster);

  void setActionScope(String scope);

  void setDefaultButton(JButton button);

  boolean isModal();
}
