package com.almworks.api.actions;

import com.almworks.api.application.Attachment;
import com.almworks.api.edit.ItemSyncSupport;
import com.almworks.engine.gui.AttachmentsEnv;
import com.almworks.integers.LongArray;
import com.almworks.integers.WritableLongListIterator;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.sync.*;
import com.almworks.util.ui.DialogsUtil;
import com.almworks.util.ui.actions.*;

import java.util.List;

public class DeleteAttachmentAction extends SimpleAction {
  public DeleteAttachmentAction() {
    super("Delete Attachment", null);
    watchRole(AttachmentsEnv.ATTACHMENT);
    watchModifiableRole(SyncManager.MODIFIABLE);
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    ItemActionUtils.updateForEditSingleItem(context, AttachmentsEnv.ATTACHMENT);
    List<Attachment> attachments = getLocalAttachments(context);
    CantPerformException.ensureNotEmpty(attachments);
    context.putPresentationProperty(PresentationKey.NAME, attachments.size() == 1 ? "Delete Attachment" : "Delete " + attachments.size() + " Attachments");
  }

  private static List<Attachment> getLocalAttachments(ActionContext context) throws CantPerformException {
    return Attachment.LOCAL.filterList(context.getSourceCollection(AttachmentsEnv.ATTACHMENT));
  }

  @Override
  protected void doPerform(ActionContext context) throws CantPerformException {
    SyncManager syncMan = context.getSourceObject(SyncManager.ROLE);

    List<Attachment> local = getLocalAttachments(context);
    ItemSyncSupport.prepareEditOrShowLockingEditor(context, local);
    confirm(context, local);
    final LongArray itemsToDelete = new LongArray();
    for (Attachment a : local) {
      itemsToDelete.add(a.getItem());
    }
    CantPerformException.ensure(!itemsToDelete.isEmpty());
    syncMan.commitEdit(itemsToDelete, new EditCommit.Adapter() {
      @Override
      public void performCommit(EditDrain drain) throws DBOperationCancelledException {
        for (WritableLongListIterator i = itemsToDelete.iterator(); i.hasNext();) {
          drain.changeItem(i.next()).delete();
        }
      }
    });
  }

  private static void confirm(ActionContext context, List<Attachment> local) throws CantPerformExceptionExplained {
    if (local.isEmpty())
      throw new CantPerformExceptionExplained("No attachment can be deleted.");
    String message = local.size() == 1 ? "Are you sure you want to delete " + local.get(0).getFilename() + "?" :
      "Are you sure you want to delete " + local.size() + " attachments?";
    if (!DialogsUtil.askConfirmation(context.getComponent(), message, "Delete Attachments"))
      throw new CantPerformExceptionSilently("Cancelled");
  }
}
