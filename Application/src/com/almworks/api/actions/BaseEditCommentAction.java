package com.almworks.api.actions;

import com.almworks.api.application.ItemUiModel;
import com.almworks.api.application.UiItem;
import com.almworks.api.gui.*;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.util.ui.actions.*;
import org.almworks.util.detach.DetachComposite;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;

public abstract class BaseEditCommentAction extends BaseEditItemAction {
  public BaseEditCommentAction(String text, Icon icon, DataRole<? extends UiItem> commentRole) {
    super(text, icon, commentRole);
  }

  protected BasicWindowBuilder setupWindowBuilder(WindowManager manager, ItemUiModelImpl guiModel,
    final ActionContext context) throws CantPerformException {

    DetachComposite life = new DetachComposite();
    CommentEditorDialog dialog = setupCommentEditor(life, context, guiModel);
    ItemActionUtils.installItemModelProvider(dialog.getComponent(), guiModel);
    DialogBuilder builder = dialog.getBuilder();
    builder.detachOnDispose(life);
    return builder;
  }

  protected abstract CommentEditorDialog setupCommentEditor(Lifespan life, ActionContext context, ItemUiModel guiModel)
    throws CantPerformException;
}