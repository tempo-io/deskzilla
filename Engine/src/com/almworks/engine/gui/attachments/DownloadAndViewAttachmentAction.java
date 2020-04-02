package com.almworks.engine.gui.attachments;

import com.almworks.api.application.Attachment;
import com.almworks.api.download.DownloadedFile;
import com.almworks.engine.gui.AttachmentsEnv;
import com.almworks.util.ui.actions.*;

import java.util.List;

import static com.almworks.api.download.DownloadedFile.State.READY;

class DownloadAndViewAttachmentAction extends AbstractAttachmentAction {
  public static final AnAction INSTANCE = new DownloadAndViewAttachmentAction();

  private DownloadAndViewAttachmentAction() {
    super("View Attachments");
    watchRole(AttachmentsEnv.ATTACHMENT);
    watchRole(AttachmentsController.ROLE);
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    List<Attachment> attachments = context.getSourceCollection(AttachmentsEnv.ATTACHMENT);
    boolean download = false;
    int size = attachments.size();
    boolean enabled = false;
    if (size > 0) {
      AttachmentsController controller = context.getSourceObject(AttachmentsController.ROLE);
      for (Attachment attachment : attachments) {
        if (attachment.isLocal()) {
          if (AttachmentsControllerUtil.isGoodFile(attachment.getFileForUpload())) {
            enabled = true;
          }
        } else {
          DownloadedFile dfile = controller.getDownloadedFile(attachment);
          if (AttachmentsControllerUtil.isDownloadNeeded(dfile)) {
            download = true;
            enabled = true;
          } else if (dfile != null) {
            if (dfile.getState() != READY || AttachmentsControllerUtil.isGoodFile(dfile.getFile())) {
              enabled = true;
            }
          }
        }
      }
    }
    context.setEnabled(enabled);
    String name = download ? "Download and View Attachment" : "View Attachment";
    if (size != 1)
      name += "s";
    context.putPresentationProperty(PresentationKey.NAME, name);
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    List<Attachment> attachments = context.getSourceCollection(AttachmentsEnv.ATTACHMENT);
    AttachmentsController controller = context.getSourceObject(AttachmentsController.ROLE);
    for (Attachment attachment : attachments) {
      controller.showAttachment(attachment, context.getComponent());
    }
  }
}
