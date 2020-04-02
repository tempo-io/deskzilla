package com.almworks.actions.create;

import com.almworks.actions.ConfirmEditDialog;
import com.almworks.api.actions.BaseCommitAction;
import com.almworks.api.actions.ItemActionUtils;
import com.almworks.api.application.ModelKeyVerifier;
import com.almworks.api.edit.EditLifecycle;
import com.almworks.api.edit.ItemCreator;
import com.almworks.api.engine.Connection;
import com.almworks.api.gui.*;
import com.almworks.edit.ItemEditUtil;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.integers.LongArray;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.actions.*;
import org.almworks.util.StringUtil;

import javax.swing.*;

class CreateItemCopyAction extends BaseCommitAction {
  private final boolean myUpload;

  private CreateItemCopyAction(String text, Icon icon, boolean upload) {
    super(text, icon);
    myUpload = upload;
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    final ItemUiModelImpl itemModel = ItemActionUtils.getModel(context);
    boolean verified = verifyItem(context, itemModel);

    AggregatingEditCommit commit = new AggregatingEditCommit();
    addCreateAndUpload(context, itemModel, commit, myUpload && verified);
    addOnSuccessProcedures(commit, context, itemModel);

    EditLifecycle editLife = context.getSourceObject(EditLifecycle.ROLE);
    editLife.commit(context, commit);
  }

  private boolean verifyItem(ActionContext context, ItemUiModelImpl artifactModel) throws CantPerformException {
    PropertyMap values = artifactModel.takeSnapshot();
    StringBuilder errors = null;

    String sep = "";
    for (ModelKeyVerifier ver : artifactModel.getMetaInfo().getVerifierManager().getVerifiers()) {
      if (ver == null) { assert false; continue; }
      String err = ver.verify(values);
      if (err != null && !err.isEmpty()) {
        //noinspection ConstantConditions
        errors = TextUtil.append(errors, sep).append(err);
        sep = StringUtil.LINE_SEPARATOR;
      }
    }

    if (errors != null) {
      DialogManager dialogManager = context.getSourceObject(DialogManager.ROLE);
      DialogBuilder dialog = dialogManager.createBuilder("createArtifact.confirmEdit");
      ConfirmEditDialog.Result res = ConfirmEditDialog.show(dialog, myUpload, errors.toString(), false);
      if (res.isContinueEdit()) {
        throw new CantPerformExceptionSilently("User chose to continue editing");
      }
      return res.isUpload();
    }
    return true;
  }

  private static void addCreateAndUpload(ActionContext context, final ItemUiModelImpl itemModel, AggregatingEditCommit commit, final boolean uploadOnSuccess) throws CantPerformException {
    final DialogManager dialogMan = context.getSourceObject(DialogManager.ROLE);
    final Connection conn = itemModel.getConnection();
    commit.addProcedure(null, new EditCommit.Adapter() {
      long createdItem = 0L;
      @Override
      public void performCommit(EditDrain drain) throws DBOperationCancelledException {
        ItemVersionCreator creator = ItemEditUtil.copyPrototype(drain, itemModel.getItem());
        ItemEditUtil.addModelChanges(creator, itemModel, dialogMan);
        createdItem = creator.getItem();
      }

      @Override
      public void onCommitFinished(boolean success) {
        if (uploadOnSuccess && success && conn != null && createdItem > 0L) {
          conn.uploadItems(LongArray.singleton(createdItem));
        }
      }
    });
  }

  private void addOnSuccessProcedures(AggregatingEditCommit commit, ActionContext context, final ItemUiModelImpl itemModel) throws CantPerformException {
    final ItemCreator itemCreator = context.getSourceObject(ItemCreator.ROLE);
    commit.addProcedure(ThreadGate.AWT, new EditCommit.Adapter() {
      @Override
      public void onCommitFinished(boolean success) {
        if(success) {
          itemCreator.valueCommitted(itemModel.takeSnapshot());
        }
      }
    });
    beforeCommit(itemModel, commit, context);
  }

  protected void beforeCommit(ItemUiModelImpl model, AggregatingEditCommit commit, ActionContext context) throws CantPerformException {}

  public static AnAction createSaveAction() {
    CreateItemCopyAction action = new CreateItemCopyAction(L.actionName(ItemActionUtils.SAVE_NAME), Icons.ACTION_SAVE, false) {
      protected void beforeCommit(ItemUiModelImpl model, AggregatingEditCommit commit, ActionContext context) throws CantPerformException {
        final DialogManager manager = context.getSourceObject(DialogManager.ROLE);
        commit.addProcedure(ThreadGate.AWT, new EditCommit.Adapter() {
          @Override
          public void onCommitFinished(boolean success) {
            if(success) {
              ShowOnceMessageBuilder builder = manager.createOnceMessageBuilder("newArtifactSavedMessage");
              builder.setTitle(L.frame(Local.parse("New " + Terms.ref_Artifact + " - " + Terms.ref_Deskzilla)));
              builder.setMessage(L.content(Local.parse("New " + Terms.ref_artifact + " was saved in local database.")),
                JOptionPane.INFORMATION_MESSAGE);
              builder.showMessage();
            }
          }
        });
      }
    };
    action.setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION,
      L.tooltip(Local.parse("Save new " + Terms.ref_artifact + " in local database without uploading to server")));
    return action;
  }

  public static AnAction createCommitAction() {
    CreateItemCopyAction action = new CreateItemCopyAction(L.actionName(ItemActionUtils.COMMIT_NAME), Icons.ACTION_COMMIT_ARTIFACT, true);
    action.setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION,
      L.tooltip(Local.parse("Save new $(" + Terms.key_artifact + ") and upload it to server")));
    return action;
  }
}