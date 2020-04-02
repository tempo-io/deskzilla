package com.almworks.engine.gui.attachments;

import com.almworks.api.application.Attachment;
import com.almworks.engine.gui.AttachmentsEnv;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.*;

class CopyFileURLAction extends AbstractAttachmentAction {
  public static final AnAction INSTANCE = new CopyFileURLAction();

  private CopyFileURLAction() {
    super("Copy File URL");
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    String url = getFileUrl(context);
    context.setEnabled(url != null);
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    String url = getFileUrl(context);
    if (url != null) {
      UIUtil.copyToClipboard(url);
    }
  }

  private static String getFileUrl(ActionContext context) throws CantPerformException {
    Attachment attachment = context.getSourceObject(AttachmentsEnv.ATTACHMENT);
    return attachment.getUrl();
  }
}
