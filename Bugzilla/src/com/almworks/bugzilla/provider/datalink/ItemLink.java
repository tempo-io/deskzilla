package com.almworks.bugzilla.provider.datalink;

import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.sync.BugBox;
import com.almworks.items.sync.*;

public interface ItemLink {
  void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff change, BugInfoForUpload updateInfo) throws UploadNotPossibleException;

  /**
   * Checks if upload of corresponding items is successful. Should call {@link com.almworks.items.sync.UploadDrain#setAllDone(long)}
   * or {@link com.almworks.items.sync.UploadDrain#cancelUpload(long)} to allow update DB with downloaded values.
   * @param drain upload process transaction
   * @param box uploaded bug
   */
  void checkUpload(UploadDrain drain, BugBox box);
}
