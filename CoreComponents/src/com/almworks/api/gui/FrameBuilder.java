package com.almworks.api.gui;

import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.ui.UIComponentWrapper;

import java.util.Properties;

/**
 * @author : Dyoma
 */
public interface FrameBuilder extends BasicWindowBuilder<UIComponentWrapper> {
  void setMenu(ReadonlyConfiguration menuContent, Properties i18n);

  /**
   * Instruct the builder to install a {@code GlobalDataRoot}
   * on the frame's root pane upon creating the frame.
   * By default it is not installed.
   * @param setRoot {@code true} to install a global data root.
   */
  void setRootPaneGlobalRoot(boolean setRoot);

  /**
   * By default, resizable frame is created.
   * */
  void setResizable(boolean resizable);
}
