package com.almworks.engine.gui.attachments;

import com.almworks.api.application.Attachment;
import com.almworks.api.download.DownloadedFile;
import com.almworks.engine.gui.AttachmentsEnv;
import com.almworks.util.ui.actions.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.io.File;

import static com.almworks.api.download.DownloadedFile.State.READY;

abstract class AbstractAttachmentAction extends SimpleAction {
  public AbstractAttachmentAction(@Nullable String name) {
    super(name);
    watchRole(AttachmentsEnv.ATTACHMENT);
  }

  public AbstractAttachmentAction(@Nullable String name, @Nullable Icon icon) {
    super(name, icon);
  }

  @Nullable
  protected static File getGoodFile(ActionContext context) throws CantPerformException {
    Attachment attachment = context.getSourceObject(AttachmentsEnv.ATTACHMENT);
    if (attachment.isLocal()) {
      File uploadFile = attachment.getFileForUpload();
      if (AttachmentsControllerUtil.isGoodFile(uploadFile)) {
        return uploadFile;
      }
    } else {
      AttachmentsController controller = context.getSourceObject(AttachmentsController.ROLE);
      DownloadedFile dfile = controller.getDownloadedFile(attachment);
      if (dfile != null && dfile.getState() == READY) {
        File file = dfile.getFile();
        if (AttachmentsControllerUtil.isGoodFile(file)) {
          return file;
        }
      }
    }
    return null;
  }
}
