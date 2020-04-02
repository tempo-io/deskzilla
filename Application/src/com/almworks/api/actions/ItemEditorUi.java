package com.almworks.api.actions;

import com.almworks.api.application.ItemUiModel;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.properties.Role;
import com.almworks.util.ui.UIComponentWrapper2;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.jetbrains.annotations.*;

/**
 * @author dyoma
 */
public interface ItemEditorUi extends UIComponentWrapper2 {
  Role<ItemEditorUi> ROLE = Role.role(ItemEditorUi.class);

  @Nullable
  String getSaveProblem();

  void copyValues(ItemUiModel model) throws CantPerformExceptionExplained;

  /**
   * Should return true if the user must be asked to confirm the cancel edit action.
   */
  boolean isConsiderablyModified();

  Modifiable getModifiable();
}
