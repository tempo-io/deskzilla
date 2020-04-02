package com.almworks.bugzilla.integration.data;

import com.almworks.items.sync.ItemUploader;

public interface SuccessfulUploadHook {
  void onSuccessfulUpload(Integer attachmentID, String attachmentURL, BugInfoForUpload.AttachData attachment,
    ItemUploader.UploadProcess upload);
}
