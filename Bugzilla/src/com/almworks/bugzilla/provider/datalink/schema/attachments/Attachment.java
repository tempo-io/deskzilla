package com.almworks.bugzilla.provider.datalink.schema.attachments;

import com.almworks.api.download.DownloadManager;
import com.almworks.api.download.DownloadedFile;
import com.almworks.api.misc.WorkArea;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.integration.data.SuccessfulUploadHook;
import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.bugzilla.provider.datalink.ItemLink;
import com.almworks.bugzilla.provider.datalink.UploadNotPossibleException;
import com.almworks.bugzilla.provider.sync.BugBox;
import com.almworks.engine.gui.attachments.AttachmentUtils;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.api.DBResult;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.files.FileUtil;
import org.almworks.util.Log;

import java.io.File;
import java.io.IOException;

public class Attachment implements ItemLink {
  @Override
  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff change, BugInfoForUpload updateInfo) throws
    UploadNotPossibleException
  {
    if (!buildUploadInfo(change, updateInfo)) {
      prepare.removeFromUpload(change.getItem());
    }
  }

  @Override
  public void checkUpload(UploadDrain drain, BugBox box) {
    SyncUtils.setAllUploaded(drain, AttachmentsLink.typeAttachment);
  }

  private boolean buildUploadInfo(ItemDiff change, BugInfoForUpload info) {
    WorkArea workArea = CommonMetadata.getContainer().getActor(WorkArea.APPLICATION_WORK_AREA);
    if (workArea == null) {
      assert false : this;
      return false;
    }

    final ItemVersion revision = change.getNewerVersion();
    final Integer id = revision.getValue(AttachmentsLink.attrId);
    if (id != null) {
      return false;
    }

    final String localPlacement = revision.getValue(AttachmentsLink.attrLocalPath);
    if (localPlacement == null) {
      Log.warn("cannot upload attachment " + change.getItem() + ": no local placement");
      return false;
    }

    final File file = new File(workArea.getUploadDir(), localPlacement);
    if (!file.isFile()) {
      Log.warn("cannot upload attachment " + change.getItem() + ": no file " + file);
      return false;
    }
    if (!file.canRead()) {
      Log.warn("cannot upload attachment " + change.getItem() + ": file " + file + " cannot be read");
      return false;
    }

    final String description = revision.getValue(AttachmentsLink.attrDescription);
    final String mimeType = revision.getValue(AttachmentsLink.attrMimeType);

    final SuccessfulUploadHook hook = new SuccessfulUploadHook() {
      final long attachmentItem = revision.getItem();

      @Override
      public void onSuccessfulUpload(Integer attachmentID, String attachmentURL, BugInfoForUpload.AttachData attachment,
        ItemUploader.UploadProcess upload)
      {
        Log.debug("successfully uploaded attachment: id " + attachmentID);

        DownloadManager downloadManager = CommonMetadata.getContainer().requireActor(DownloadManager.ROLE);
        try {
          DownloadedFile downloadedFile = downloadManager.storeDownloadedFile(attachmentURL, file, mimeType);
          // removing temp file in upload area
          AttachmentUtils.deleteUploadFile(file);
          updateUploadedAttachmentArtifact(mimeType, attachmentItem, downloadedFile, attachmentID, upload);
        } catch (IOException e) {
          Log.warn("cannot store uploaded file in download dir", e);
        }
      }
    };
    info.addAttachment(file, description, mimeType, hook);
    return true;
  }

  private void updateUploadedAttachmentArtifact(final String mimeType, final long item, final DownloadedFile downloadedFile, final Integer attachmentID,
    ItemUploader.UploadProcess upload)
  {
    upload.writeUploadState(new DownloadProcedure<UploadDrain>() {
      @Override
      public void write(UploadDrain drain) throws DBOperationCancelledException {
        ItemVersionCreator creator = drain.setAllDone(item);
        creator.setAlive();
        creator.setValue(AttachmentsLink.attrId, attachmentID);
        creator.setValue(AttachmentsLink.attrLocalPath, null);
        creator.setValue(AttachmentsLink.attrMimeType, mimeType);

        final File file = downloadedFile.getFile();
        if (file != null) {
          creator.setValue(AttachmentsLink.attrFileName, file.getName());
          final long length = file.length();
          if (length > 0) {
            creator.setValue(AttachmentsLink.attrSize, FileUtil.getSizeString(length));
          }
        }
      }

      @Override
      public void onFinished(DBResult<?> result) {
      }
    });
  }
}
