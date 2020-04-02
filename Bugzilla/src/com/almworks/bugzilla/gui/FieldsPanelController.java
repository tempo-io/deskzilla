package com.almworks.bugzilla.gui;

import com.almworks.api.application.ModelMap;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.api.application.viewer.UIController;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;

/**
 * This is not a utility class! It cannot be reused. It *knows* which controller is responsible for what actions.
 */
class FieldsPanelController implements UIController<JPanel> {
  public static void install(@NotNull JPanel component, @NotNull UIController<JPanel> timeController,
    @NotNull UIController<JPanel> customFieldsController, @NotNull UIController<JPanel> optionalFieldsController,
    @NotNull UIController<JPanel> flagsController)
  {
    UIController.CONTROLLER.putClientValue(component,
      new FieldsPanelController(timeController, customFieldsController, optionalFieldsController, flagsController));
  }

  private final UIController<JPanel> myTimeController;
  private final UIController<JPanel> myCustomFieldsController;
  private final UIController<JPanel> myOptionalFieldsController;
  private final UIController<JPanel> myFlagsController;

  public FieldsPanelController(
    @NotNull UIController<JPanel> timeController,
    @NotNull UIController<JPanel> customFieldsController,
    @NotNull UIController<JPanel> optionalFieldsController,
    @NotNull UIController<JPanel> flagsController)
  {
    myTimeController = timeController;
    myCustomFieldsController = customFieldsController;
    myOptionalFieldsController = optionalFieldsController;
    myFlagsController = flagsController;
  }

  public void connectUI(@NotNull Lifespan lifespan, @NotNull ModelMap model, @NotNull JPanel component) {
    // 1. Add optional fields (only used ones)
    myOptionalFieldsController.connectUI(lifespan, model, component);

    // 2. Add flag viewer.
    myFlagsController.connectUI(lifespan, model, component);

    // 3. Probably add time controls
    myTimeController.connectUI(lifespan, model, component);

    // 4. Connect standard fields and time controls
    DefaultUIController.connectChildren(component, lifespan, model);

    // 5. Custom fields will be connected by their controller explicitly
    myCustomFieldsController.connectUI(lifespan, model, component);
  }
}
