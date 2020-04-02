package com.almworks.actions;

import com.almworks.api.actions.ItemActionUtils;
import com.almworks.api.application.*;
import com.almworks.api.download.DownloadManager;
import com.almworks.api.download.DownloadedFile;
import com.almworks.util.Terms;
import com.almworks.util.ui.actions.*;

import java.util.Collection;
import java.util.List;

import static com.almworks.api.download.DownloadedFile.State.*;

public class DownloadAttachmentsAction extends SimpleAction {
  public DownloadAttachmentsAction() {
    super("Down&load Attachments");
    setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, "Download all attachments for selected " + Terms.ref_artifacts);
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    List<ItemWrapper> wrappers = ItemActionUtils.basicUpdate(context);
    AttachmentInfoKey key = context.getSourceObject(AttachmentInfoKey.ROLE);
    DownloadManager downloadManager = context.getSourceObject(DownloadManager.ROLE);
    boolean enabled = false;
    for (ItemWrapper wrapper : wrappers) {
      Collection<? extends Attachment> attachments = wrapper.getModelKeyValue(key);
      if (attachments != null && attachments.size() > 0) {
        for (Attachment attachment : attachments) {
          String url = attachment.getUrl();
          if (url != null) {
            DownloadedFile.State state = downloadManager.getDownloadStatus(url).getState();
            if (needsDownload(state)) {
              enabled = true;
              break;
            }
          }
        }
      }
    }
    context.setEnabled(enabled);
  }

  private static boolean needsDownload(DownloadedFile.State state) {
    return state != DOWNLOADING && state != QUEUED && state != READY;
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    List<ItemWrapper> wrappers = context.getSourceCollection(ItemWrapper.ITEM_WRAPPER);
    AttachmentInfoKey key = context.getSourceObject(AttachmentInfoKey.ROLE);
    DownloadManager downloadManager = context.getSourceObject(DownloadManager.ROLE);
    for (ItemWrapper wrapper : wrappers) {
      Collection<? extends Attachment> attachments = wrapper.getModelKeyValue(key);
      if (attachments != null && attachments.size() > 0) {
        for (Attachment aa : attachments) {
          String url = aa.getUrl();
          if (url != null) {
            DownloadedFile.State state = downloadManager.getDownloadStatus(url).getState();
            if (needsDownload(state)) {
              downloadManager.initiateDownload(url, aa.createDownloadRequest(), false, false);
            }
          }
        }
      }
    }
  }
}
