package com.almworks.actions.merge2;

import com.almworks.api.actions.*;
import com.almworks.api.edit.EditLifecycle;
import com.almworks.api.gui.DialogManager;
import com.almworks.api.gui.MainMenu;
import com.almworks.edit.ItemEditUtil;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.util.L;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;
import com.almworks.util.ui.swing.Shortcuts;

/**
 * @author : Dyoma
 */
class CommitMergeAction extends BaseCommitAction {
  public static final AnAction INSTANCE = new CommitMergeAction();

  private CommitMergeAction() {
    super(L.actionName(ItemActionUtils.COMMIT_NAME), Icons.ACTION_COMMIT_ARTIFACT);
    setDefaultPresentation(PresentationKey.SHORTCUT, Shortcuts.CTRL_ENTER);
    setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION,
      L.tooltip("Save merge result and upload changes to server"));
  }

  @Override
  protected void customUpdate(UpdateContext context) throws CantPerformException {
    super.customUpdate(context);
    IdActionProxy.setShortcut(context, MainMenu.ItemEditor.COMMIT);
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    final ItemUiModelImpl model = ItemActionUtils.getModel(context);
    EditLifecycle edit = context.getSourceObject(EditLifecycle.ROLE);
    AggregatingEditCommit commit = new AggregatingEditCommit();
    final LongList items = edit.getEditingItems();
    commit.addProcedure(null, new CommitMerge(model, context.getSourceObject(DialogManager.ROLE)));
    commit.addProcedure(null, UploadOnSuccess.create(items));
    edit.commit(context, commit);
  }

  private static class CommitMerge extends EditCommit.Adapter {
    private final ItemUiModelImpl myModel;
    private final DialogManager myDialogMan;

    public CommitMerge(ItemUiModelImpl model, DialogManager dialogMan) {
      myModel = model;
      myDialogMan = dialogMan;
    }

    @Override
    public void performCommit(EditDrain drain) throws DBOperationCancelledException {
      LongList items = drain.forItem(myModel.getItem()).getSlavesRecursive();
      for (int i = 0; i < items.size(); i++) drain.markMerged(items.get(i));
      ItemVersionCreator creator = drain.markMerged(myModel.getItem());
      ItemEditUtil.addModelChanges(creator, myModel, myDialogMan);
    }
  }
}
