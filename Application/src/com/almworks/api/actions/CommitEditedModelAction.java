package com.almworks.api.actions;

import com.almworks.api.edit.EditLifecycle;
import com.almworks.api.gui.DialogBuilder;
import com.almworks.api.gui.DialogManager;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.util.commons.Factory;
import com.almworks.util.ui.actions.*;
import org.jetbrains.annotations.*;

import static com.almworks.api.actions.ItemActionUtils.*;

public class CommitEditedModelAction extends BaseCommitAction {
  private final boolean myUpload;

  public CommitEditedModelAction(boolean upload) {
    myUpload = upload;
    watchModifiableRole(EditLifecycle.MODIFIABLE);
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    doCommit(context, myUpload);
  }

  /**
   * Performs commit without upload and if it's the first time, notifies user about it.
   */
  public static void commitAndShowMessageOnce(ActionContext context, @NotNull String messageId) throws CantPerformException {
    doCommit(context, false, messageId);
  }

  public static void doCommit(ActionContext context, boolean upload) throws CantPerformException {
    doCommit(context, upload, null);
  }

  public static void doCommit(ActionContext context, boolean upload, @Nullable String messageId) throws CantPerformException {
    final ItemUiModelImpl model = getModel(context);
    final Factory<DialogBuilder> builder = getDialogBuilder(context, "EditItem.confirmEdit");
    doCommit(context, model, false, upload, messageId, builder);
  }

  public static void doCommit(ActionContext context, ItemUiModelImpl model, boolean unsafe, boolean upload,
    @Nullable String messageId, Factory<DialogBuilder> confirmBuilder) throws CantPerformException
  {
    final boolean verified = verifyLastArtifactEdit(model, upload, confirmBuilder);
    AggregatingEditCommit commit = new AggregatingEditCommit();
    commit.addProcedure(null, CommitModel.create(model, context.getSourceObject(DialogManager.ROLE)));
    if (upload && verified) model.uploadAfterCommit(commit);
    showMessageOnce(context, commit, upload, messageId);
    EditLifecycle editLife = context.getSourceObject(EditLifecycle.ROLE);
    editLife.commit(context, commit, unsafe);
  }

  private static void showMessageOnce(ActionContext context, AggregatingEditCommit commit, boolean upload, String messageId) throws CantPerformException {
    if (!upload && messageId != null) SavedToLocalDBMessage.addTo(context, commit, messageId);
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    super.customUpdate(context);
    context.setEnabled(ItemActionUtils.getModel(context).isChanged());
  }
}
