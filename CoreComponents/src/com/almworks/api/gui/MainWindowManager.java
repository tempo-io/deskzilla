package com.almworks.api.gui;

import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.properties.Role;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Properties;

/**
 * @author : Dyoma
 */
public interface MainWindowManager {
  Role<MainWindowManager> ROLE = Role.role(MainWindowManager.class);

  void setContentComponent(@Nullable JComponent component);

  void showWindow(boolean show);

  StatusBar getStatusBar();

  // kludge: dyoma review - see AboutDialog
  JFrame getMainFrame();

  /**
   * <p>On Mac OS X returns a factory that produces instances of
   * {@code JMenuBar} with excatly the same structure as the main
   * window menu bar. On other platforms returns a factory that
   * produces {@code null}s.
   * <p>The factory is used by {@code FrameBuilderImpl} to attach
   * default menu bars to frames.
   * @return The main menu bar factory.
   */
//  Factory<JMenuBar> getMenuFactory();

  void bringToFront();

  void minimize();

  void setHideOnMinimizeAndClose(boolean hide);

  interface WindowDescriptor {
    Role<WindowDescriptor> ROLE = Role.role(WindowDescriptor.class);
    
    ReadonlyConfiguration getMainMenu();

    String getWindowTitle();

    Properties getMainMenuI18N();
  }
}
