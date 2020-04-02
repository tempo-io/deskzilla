package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.datalink.ItemLink;
import com.almworks.bugzilla.provider.datalink.UploadNotPossibleException;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.bugzilla.provider.sync.BugBox;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.SyncUtils;
import org.almworks.util.Log;

class FlagSlave implements ItemLink {
  @Override
  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff change, BugInfoForUpload updateInfo) throws UploadNotPossibleException
  {
    ItemVersion newer = change.getNewerVersion();
    SyncState state = newer.getSyncState();
    switch (state) {
    case SYNC: return;
    case NEW: uploadFlag(newer, updateInfo, null, true); return;
    case EDITED: uploadFlag(newer, updateInfo, null, false); return;
    case LOCAL_DELETE: uploadFlag(newer, updateInfo, 'X', false); return;
    case DELETE_MODIFIED:
    case MODIFIED_CORPSE:
    case CONFLICT:
    default:
      Log.error("Try to upload conflicting flag " + state);
      prepare.removeFromUpload(change.getItem());
    }
  }

  @Override
  public void checkUpload(UploadDrain drain, BugBox box) {
    SyncUtils.setAllUploaded(drain, Flags.KIND_FLAG);
  }

  private void uploadFlag(ItemVersion flag, BugInfoForUpload info, Character status, boolean create)
    throws UploadNotPossibleException {
    String requestee = getRequesteeString(flag);
    ItemVersion type = flag.readValue(Flags.AT_FLAG_TYPE);
    if (type == null) throw internalError(flag);
    String name = type.getValue(Flags.AT_TYPE_NAME);
    if (name == null) throw internalError(flag);
    if (status == null) status = flag.getValue(Flags.AT_FLAG_STATUS);
    if (status == null) throw internalError(flag);
    if (create) {
      Integer typeId = type.getValue(Flags.AT_TYPE_ID);
      if (typeId == null) throw internalError(flag);
      info.createFlag(name, typeId, status, requestee);
    } else {
      Integer flagId = flag.getValue(Flags.AT_FLAG_ID);
      if (flagId == null) throw internalError(flag);
      if (status == 'X') info.changeFlag(name, flagId, status, null);
      else info.changeFlag(name, flagId, status, requestee);
    }
  }

  private String getRequesteeString(ItemVersion flag) throws UploadNotPossibleException {
    ItemVersion user = flag.readValue(Flags.AT_FLAG_REQUESTEE);
    if (user == null) return null;
    String userId = User.getRemoteId(user);
    if (userId == null) throw internalError(flag);
    return userId;
  }

  private UploadNotPossibleException internalError(ItemVersion flag) {
    return new UploadNotPossibleException("internal error uploading flag " + flag.getValue(Flags.AT_FLAG_ID));
  }
}
