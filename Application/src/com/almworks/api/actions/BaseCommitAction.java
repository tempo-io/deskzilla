package com.almworks.api.actions;

import com.almworks.api.edit.EditLifecycle;
import com.almworks.api.gui.WindowController;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.util.ui.actions.*;
import com.almworks.util.ui.swing.Shortcuts;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import javax.swing.*;

/**
 * @author dyoma
 */
public abstract class BaseCommitAction extends SimpleAction {
  public static final AnAction DISCARD = ItemActionUtils.setupCancelEditAction(
    new SimpleAction() {
      @Override
      protected void customUpdate(UpdateContext context) throws CantPerformException {
        context.putPresentationProperty(PresentationKey.SHORTCUT, Shortcuts.ESCAPE);
      }
      @Override
      protected void doPerform(ActionContext context) throws CantPerformException {
        if (context.getAvailableRoles().contains(EditLifecycle.ROLE)) {
          context.getSourceObject(EditLifecycle.ROLE).discardEdit(context);
        } else {
          Log.warn("Commit action: no edit lifecycle (" + tryGetWindowTitle(context) + ')');
          assert false;
          WindowController.CLOSE_ACTION.perform(context);
        }
      }
    });

  @NotNull
  private static String tryGetWindowTitle(ActionContext context) {
    String windowTitle = "";
    try {
      WindowController wc = context.getSourceObject(WindowController.ROLE);
      windowTitle = wc.getTitle();
    } catch (CantPerformException ex) {
    }
    return windowTitle;
  }

  protected BaseCommitAction() {
    watchModifiableRole(EditLifecycle.MODIFIABLE);
    watchModifiableRole(ItemUiModelImpl.ROLE);
  }

  protected BaseCommitAction(String text, Icon icon) {
    super(text, icon);
    watchModifiableRole(EditLifecycle.MODIFIABLE);
    watchModifiableRole(ItemUiModelImpl.ROLE);
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    context.getSourceObject(EditLifecycle.ROLE).checkCommitAction();
  }
}
