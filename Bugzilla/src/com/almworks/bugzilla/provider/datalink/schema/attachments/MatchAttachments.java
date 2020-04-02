package com.almworks.bugzilla.provider.datalink.schema.attachments;

import com.almworks.api.download.DownloadManager;
import com.almworks.api.download.DownloadedFile;
import com.almworks.bugzilla.integration.BugzillaDateUtil;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.LoadedObject;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.items.sync.DBDrain;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.util.commons.Procedure;
import com.almworks.util.config.ConfigurationException;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.files.FileUtil;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

public class MatchAttachments {
  private static final LoadedObject.AttributeSet ATTRS =
    new LoadedObject.AttributeSet(AttachmentsLink.typeAttachment, AttachmentsLink.attrMaster)
      .addAttribute(AttachmentsLink.attrId, null)
      .addAttribute(AttachmentsLink.attrDate, null)
      .addAttribute(AttachmentsLink.attrDescription, "")
      .addItemReference(AttachmentsLink.attrSubmitter)
      .addAttribute(AttachmentsLink.attrSize, "")
      .addAttribute(AttachmentsLink.attrMimeType, null)
      .addAttribute(AttachmentsLink.attrFileName, null)
      .fix();

  private final ItemVersionCreator myBug;
  private final BugInfo myInfo;
  private final BugzillaContext myContext;

  public MatchAttachments(ItemVersionCreator bug, BugInfo info, @NotNull BugzillaContext context) {
    myBug = bug;
    myInfo = info;
    myContext = context;
  }

  public void perform() {
    List<LoadedObject.Writable> db = ATTRS.changeServerSlaves(myBug);
    List<BugInfo.Attachment> server = Collections15.arrayList(myInfo.fetchAttachments());
    if (server.isEmpty()) {
      LoadedObject.deleteAll(db);
      return;
    }
    sureMatch(db, server);
    matchById(db, server);
    matchLocalNew(db, server);
    createNewDownloaded(server);
    LoadedObject.deleteAll(db);
  }

  private void createNewDownloaded(List<BugInfo.Attachment> server) {
    for (BugInfo.Attachment attachment : server) {
      Integer id = attachment.getId();
      assert id != null;
      createAttachment(attachment, id);
    }
  }

  private void matchLocalNew(List<LoadedObject.Writable> db, List<BugInfo.Attachment> server) {
    List<LoadedObject.Writable> local = LoadedObject.selectNew(db);
    db.removeAll(local);
    String thisUser = myContext.getPrivateMetadata().getThisUserId();
    for (Iterator<BugInfo.Attachment> it = server.iterator(); it.hasNext();) {
      BugInfo.Attachment attachment = it.next();
      Date date = BugzillaDateUtil.parseOrWarn(attachment.date, myInfo.getDefaultTimezone());
      Integer id = attachment.getId();
      assert id != null;
      BugzillaUser email = findSubmitterEmail(id, date);
      if (email == null) continue;
      if (!email.equalId(thisUser)) continue;
      LoadedObject.Writable dbAttach = null;
      for (LoadedObject.Writable l : local) {
        if (equalDescription(attachment, l) && Util.equals(attachment.filename, l.getValue(AttachmentsLink.attrFileName))) {
          dbAttach = l;
          break;
        }
      }
      if (dbAttach == null) continue;
      updateAttachment(attachment, dbAttach, id);
      local.remove(dbAttach);
      it.remove();
    }
  }

  private void matchById(List<LoadedObject.Writable> db, List<BugInfo.Attachment> server) {
    for (Iterator<BugInfo.Attachment> it = server.iterator(); it.hasNext();) {
      BugInfo.Attachment attachment = it.next();
      Integer id = attachment.getId();
      assert id != null;
      int index = LoadedObject.findBy(-1, db, AttachmentsLink.attrId, id);
      if (index >= 0) {
        updateAttachment(attachment, db.remove(index), id);
        it.remove();
      }
    }
  }

  private void sureMatch(List<LoadedObject.Writable> db, List<BugInfo.Attachment> server) {
    for (Iterator<BugInfo.Attachment> it = server.iterator(); it.hasNext();) {
      BugInfo.Attachment attachment = it.next();
      Integer id = attachment.getId();
      if (id == null) {
        it.remove();
        continue;
      }
      LoadedObject.Writable dbAttach = findAttachment(db, id, attachment);
      if (dbAttach != null) {
        updateAttachment(attachment, dbAttach, id);
        db.remove(dbAttach);
        it.remove();
      }
    }
  }

  private void createAttachment(final BugInfo.Attachment attachment, final int id) {
    LoadedObject.Writable db = ATTRS.newSlave(myBug);
    updateAttachment(attachment, db, id);
    StoreFileData.store(attachment, myContext, myBug);
    db.setValue(AttachmentsLink.attrId, id);
  }

  private void updateAttachment(BugInfo.Attachment server, LoadedObject.Writable db, int id) {
    Long current = db.getValue(AttachmentsLink.attrSubmitter);
    Date date = BugzillaDateUtil.parseOrWarn(server.date, myInfo.getDefaultTimezone());
    db.setAlive();
    db.setValue(AttachmentsLink.attrDate, date);
    db.setValue(AttachmentsLink.attrDescription, Util.NN(server.description));
    if (server.size != null) db.setValue(AttachmentsLink.attrSize, FileUtil.getSizeString(server.size));
    db.setValueIfNotNull(AttachmentsLink.attrMimeType, server.mimetype);
    db.setValueIfNotNull(AttachmentsLink.attrFileName, server.filename);
    if(current == null || current <= 0) {
      BugzillaUser submitterEmail = findSubmitterEmail(id, date);
      if(submitterEmail != null) {
        long submitter = User.getOrCreate(myBug, submitterEmail, myContext.getPrivateMetadata());
        db.setValue(AttachmentsLink.attrSubmitter, submitter);
      }
    }
  }

  @Nullable
  private BugzillaUser findSubmitterEmail(int attachmentId, Date attachDate) {
    return findSubmitterEmail(myInfo, attachmentId, attachDate);
  }

  static BugzillaUser findSubmitterEmail(BugInfo info, int attachmentId, Date attachDate) {
    if (attachDate == null) return null;
    long attachTime = attachDate.getTime();
    Comment[] comments = info.getOrderedComments();
    int firstCommentNearDate = findComment(comments, attachTime);
    BugzillaUser candidate = null;
    for (int i = firstCommentNearDate; i < comments.length; ++i) {
      if (comments[i].getWhenDate().getTime() - attachTime > Const.MINUTE)
        break;
      String text = comments[i].getText();
      if (text.indexOf(String.valueOf(attachmentId)) < 0) continue;
      BugzillaUser result = comments[i].getWho();
      if (text.indexOf("(id=" + attachmentId + ")") >= 0) return result;
      if (text.startsWith("Created attachment")) return result;
      candidate = result;
    }
    return candidate;
  }

  private static int findComment(Comment[] sortedComments, long attachTime) {
    int r = sortedComments.length;
    if (r == 0) return r;
    assert r < 1 || Comment.TIME_ORDER.compare(sortedComments[0], sortedComments[r - 1]) <= 0;
    if (r < 5) {
      return linearSearch(sortedComments, attachTime);
    }
    return binarySearch(sortedComments, attachTime, 0, r);
  }

  private static int linearSearch(Comment[] sortedComments, long attachTime) {
    for (int i = 0; i < sortedComments.length; ++i) {
      if (sortedComments[i].getWhenDate().getTime() >= attachTime) {
        return i;
      }
    }
    return sortedComments.length;
  }

  private static int binarySearch(Comment[] sortedComments, long attachTime, int l, int r) {
    while (r > l + 1) {
      int m = l + ((r - l) >>> 1);
      long commentTime = sortedComments[m].getWhenDate().getTime();
      if (commentTime < attachTime) l = m;
      if (commentTime - attachTime < Const.MINUTE) {
        while (m > 0 && sortedComments[m - 1].getWhenDate().getTime() >= attachTime) --m;
        return m;
      }
      r = m;
    }
    return l;
  }

  @Nullable
  private LoadedObject.Writable findAttachment(List<LoadedObject.Writable> db, int id, BugInfo.Attachment attachment) {
    int index = -1;
    while (true) {
      index = LoadedObject.findBy(index, db, AttachmentsLink.attrId, id);
      if (index < 0) return null;
      LoadedObject.Writable attach = db.get(index);
      if (equalAttachments(attachment, attach, myInfo.getDefaultTimezone())) return attach;
    }
  }

  private boolean equalAttachments(BugInfo.Attachment attachment, LoadedObject r, TimeZone defaultTimezone) {
    Date date = BugzillaDateUtil.parseOrWarn(attachment.date, defaultTimezone);
    Date dbDate = r.getValue(AttachmentsLink.attrDate);
    if (dbDate == null)
      return false;
    if (Math.abs(date.getTime() - dbDate.getTime()) > 2000)
      return false;
    return equalDescription(attachment, r);
  }

  private boolean equalDescription(BugInfo.Attachment attachment, LoadedObject r) {
    String desc = Util.NN(attachment.description);
    String dbDesc = r.getValue(AttachmentsLink.attrDescription);
    desc = desc.replaceAll("[^\\w]+", "");
    dbDesc = dbDesc.replaceAll("[^\\w]+", "");
    return desc.equalsIgnoreCase(dbDesc);
  }

  private static class StoreFileData implements Procedure<Boolean> {
    private final BugInfo.Attachment myAttachment;
    private final int myId;
    private final byte[] myData;
    private final BugzillaContext myContext;

    public StoreFileData(BugInfo.Attachment attachment, int id, byte[] data, BugzillaContext pm) {
      myAttachment = attachment;
      myId = id;
      myData = data;
      myContext = pm;
    }

    @Override
    public void invoke(Boolean arg) {
      if (arg == null || !arg) return;
      try {
        OurConfiguration configuration = myContext.getConfiguration().getValue();
        if (configuration != null) {
          String attachmentURL = BugzillaIntegration.getDownloadAttachmentURL(
            BugzillaIntegration.normalizeURL(configuration.getBaseURL()), myId);
          DownloadManager downloadManager = CommonMetadata.getContainer().getActor(DownloadManager.ROLE);
          if (downloadManager != null) {
            DownloadedFile dfile = downloadManager.getDownloadStatus(attachmentURL);
            if (dfile.getState() != DownloadedFile.State.READY)
              downloadManager.storeDownloadedFile(attachmentURL, myAttachment.filename, myAttachment.mimetype, myData);
          }
        }
      } catch (ConfigurationException e) {
        // pass through
      } catch (Exception e) {
        // don't break other things
        Log.warn("cannot store attachment", e);
      }
    }

    public static void store(BugInfo.Attachment attachment, BugzillaContext context, DBDrain drain) {
      byte[] data = attachment.getDataInternal();
      Integer id = attachment.getId();
      if(data != null && id != null && attachment.filename != null && attachment.mimetype != null)
        drain.finallyDo(ThreadGate.LONG, new StoreFileData(attachment, id, data, context));
    }
  }
}
