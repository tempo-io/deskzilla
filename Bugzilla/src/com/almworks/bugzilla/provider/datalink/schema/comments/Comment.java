package com.almworks.bugzilla.provider.datalink.schema.comments;

import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.datalink.ItemLink;
import com.almworks.bugzilla.provider.datalink.UploadNotPossibleException;
import com.almworks.bugzilla.provider.sync.BugBox;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.SyncUtils;
import org.almworks.util.Log;

import java.math.BigDecimal;
import java.util.Date;

public class Comment implements ItemLink {
  @Override
  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff comment, BugInfoForUpload updateInfo) throws
    UploadNotPossibleException
  {
    SyncState state = comment.getNewerVersion().getSyncState();
    boolean remove;
    switch (state) {
    case SYNC: remove = true; break;
    case NEW: remove = !uploadNewComment(comment, updateInfo); break;
    case EDITED: remove = !uploadPrivacyChange(comment, updateInfo); break;
    case LOCAL_DELETE:
    case DELETE_MODIFIED:
    case MODIFIED_CORPSE:
    case CONFLICT:
    default:
      Log.error("Unsupported change " + state + " " + comment.getItem());
      remove = true;
    }
    if (remove) {
      prepare.removeFromUpload(comment.getItem());
    }
  }

  @Override
  public void checkUpload(UploadDrain drain, BugBox box) {
    SyncUtils.setAllUploaded(drain, CommentsLink.typeComment);
  }

  private boolean uploadPrivacyChange(ItemDiff comment, BugInfoForUpload info) {
    if (!comment.isChanged(CommentsLink.attrPrivate)) {
      return false;
    }
    Date when = comment.getNewerValue(CommentsLink.attrDate);
    String text = comment.getNewerValue(CommentsLink.attrText);
    Boolean privacy = comment.getNewerValue(CommentsLink.attrPrivate);
    if (when == null || text == null || privacy == null) {
      Log.warn("missing privacy, text or date " + text + " " + when + " " + privacy);
      return false;
    }
    info.addCommentPrivacyChange(when.getTime(), text, privacy);
    return true;
  }

  private boolean uploadNewComment(ItemDiff commentDiff, BugInfoForUpload info) {
    ItemVersion comment = commentDiff.getNewerVersion();
    String text = comment.getValue(CommentsLink.attrText);
    if (text == null) {
      Log.warn("comment is null: " + comment.getItem());
      return false;
    }
    text = text.trim();
    if (text.length() == 0) {
      Log.warn("comment is empty: " + text);
      return false;
    }
    Boolean privacy = comment.getValue(CommentsLink.attrPrivate);
    BigDecimal timeWorked = comment.getValue(CommentsLink.attrWorkTime);
    info.addComment(text, privacy, timeWorked);
    return true;
  }
}
