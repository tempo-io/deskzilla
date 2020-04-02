package com.almworks.bugzilla.provider.datalink.schema.attachments;

import com.almworks.bugzilla.BugzillaBook;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.DataLink;
import com.almworks.bugzilla.provider.datalink.UploadNotPossibleException;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.DiscardAll;
import com.almworks.items.util.DBNamespace;
import com.almworks.items.util.SyncAttributes;
import com.almworks.itemsync.MergeOperationsManager;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.i18n.LText;
import org.jetbrains.annotations.*;

import java.util.Date;

public class AttachmentsLink implements DataLink {
  private static final String X = "Bugzilla.DB.Attachments.";
  private static final LText TYPE_ATTACHMENT = BugzillaBook.text(X + "type", "Attachment");
  private static final LText ATTRIBUTE_ID = BugzillaBook.text(X + "id", "Attachment ID");
  private static final LText ATTRIBUTE_DESCRIPTION = BugzillaBook.text(X + "description", "Description");
  private static final LText ATTRIBUTE_SUBMITTER = BugzillaBook.text(X + "submitter", "Submitter");
  private static final LText ATTRIBUTE_DATE = BugzillaBook.text(X + "date", "Date");
  private static final LText ATTRIBUTE_SIZE = BugzillaBook.text(X + "size", "Size");
  private static final LText ATTRIBUTE_MIME_TYPE = BugzillaBook.text(X + "mime-type", "Mime Type");
  private static final LText ATTRIBUTE_FILE_NAME = BugzillaBook.text(X + "filename", "File Name");

  public static final DBNamespace NS = BugzillaProvider.NS.subNs("attachment");
  public static final DBItemType typeAttachment = NS.type();

  public static final DBAttribute<Long> attrMaster = NS.master("bug");
  public static final DBAttribute<Integer> attrId = NS.integer("id", ATTRIBUTE_ID.format(), false);
  public static final DBAttribute<String> attrDescription = NS.string("description", ATTRIBUTE_DESCRIPTION.format(), true);
  public static final DBAttribute<Long> attrSubmitter = NS.link("submitter", ATTRIBUTE_SUBMITTER.format(), false);
  public static final DBAttribute<Date> attrDate = NS.date("date", ATTRIBUTE_DATE.format(), false);
  public static final DBAttribute<String> attrSize = NS.string("size", ATTRIBUTE_SIZE.format(), false);
  public static final DBAttribute<String> attrMimeType = NS.string("mimeType", ATTRIBUTE_MIME_TYPE.format(), false);
  public static final DBAttribute<String> attrFileName = NS.string("fileName", ATTRIBUTE_FILE_NAME.format(), false);
  public static final DBAttribute<String> attrLocalPath = NS.string("localPath", "Local Path", false);

  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info) throws
    UploadNotPossibleException
  {
    prepare.addToUpload(bug.getNewerVersion().getSlaves(attrMaster));
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    // todo!
    return null;
  }

  @Override
  public void initializePrototype(ItemVersionCreator prototype, PrivateMetadata pm) {
  }

  public static long findAttachment(DBReader reader, long connection, int id) {
    return reader.query(
      BoolExpr.and(
        DPEquals.create(SyncAttributes.CONNECTION, connection),
        DPEquals.create(attrId, id))
    ).getItem();
  }

  public void updateRevision(PrivateMetadata pm, ItemVersionCreator bug, BugInfo info, @NotNull BugzillaContext context) {
    new MatchAttachments(bug, info, context).perform();
  }

  public static void registerMergers(MergeOperationsManager mm) {
    mm.addMergeOperation(new DiscardAll(), typeAttachment);
  }
}
