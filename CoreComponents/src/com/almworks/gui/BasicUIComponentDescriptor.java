package com.almworks.gui;

import com.almworks.api.container.RootContainer;
import com.almworks.api.gui.*;
import com.almworks.api.misc.TimeService;
import com.almworks.api.platform.ComponentDescriptor;
import com.almworks.api.platform.ProductInformation;
import com.almworks.api.tray.SystemTrayComponentDescriptor;
import com.almworks.api.tray.TrayIconService;
import com.almworks.platform.DiagnosticRecorder;
import com.almworks.util.config.JDOMConfigurator;
import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.files.FileUtil;
import com.almworks.util.properties.Role;
import com.almworks.util.ui.actions.ActionRegistry;

import java.util.Properties;

/**
 * @author : Dyoma
 */
public class BasicUIComponentDescriptor implements ComponentDescriptor {
  public void registerActors(RootContainer container) {
    container.registerActorClass(ActionRegistry.ROLE, AppActionRegistry.class);
    container.registerActorClass(MainWindowManager.WindowDescriptor.ROLE, DefaultMainMenu.class);
    container.registerActorClass(MainWindowManager.ROLE, MainWindowManagerImpl.class);
    container.registerActorClass(Role.role("aboutDialog"), AboutDialog.class);
    container.registerActorClass(WindowManager.ROLE, DefaultWindowManager.class);
    container.registerActorClass(DialogManager.ROLE, DefaultDialogManager.class);
    container.registerActorClass(Role.role("defaultActions"), DefaultActionsCollection.class);
    container.registerActorClass(TrayIconService.ROLE, SystemTrayComponentDescriptor.class);
    container.registerActorClass(TimeService.ROLE, TimeService.class);
    container.registerActorClass(DiagnosticRecorder.ROLE, DiagnosticRecorder.class);
    container.registerActorClass(Role.role("diagnosticUI"), DiagnosticUI.class);
  }

  static class DefaultMainMenu implements MainWindowManager.WindowDescriptor {
    private static final String MAIN_MENU = "com/almworks/gui/MainMenu.xml";
    private static final String MENU_118N = "com/almworks/rc/menu.properties";

    private final ProductInformation myProductInfo;

    public DefaultMainMenu(ProductInformation productInfo) {
      myProductInfo = productInfo;
    }

    @Override
    public ReadonlyConfiguration getMainMenu() {
      return JDOMConfigurator.parse(DefaultMainMenu.class.getClassLoader(), MAIN_MENU);
    }

    @Override
    public Properties getMainMenuI18N() {
      return FileUtil.loadProperties(DefaultMainMenu.class.getClassLoader(), MENU_118N);
    }

    @Override
    public String getWindowTitle() {
      return myProductInfo.getName();
    }
  }
}
