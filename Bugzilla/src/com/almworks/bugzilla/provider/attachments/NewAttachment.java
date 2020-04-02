package com.almworks.bugzilla.provider.attachments;

import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.UserChanges;
import com.almworks.api.engine.Connection;
import com.almworks.bugzilla.provider.datalink.schema.attachments.AttachmentsLink;
import com.almworks.engine.gui.attachments.AttachmentSaveException;
import com.almworks.engine.gui.attachments.AttachmentUtils;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.EditDrain;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.items.util.SyncAttributes;
import org.jetbrains.annotations.*;

import java.io.File;
import java.util.Date;

public class NewAttachment extends AbstractAttachmentInfo {
  private final File myFile;
  private final String myMimeType;
  private final String mySize;
  private final long mySizeLong;

  public NewAttachment(File file, String mimeType, long length, String size, String description) {
    super(description);
    mySizeLong = length;
    assert file != null;
    assert mimeType != null;
    myFile = file;
    myMimeType = mimeType;
    mySize = size;

    // todo - file work in awt thread :(
    assert myFile.isFile();
  }

  public long getItem() {
    return 0;
  }

  public Date getDate() {
    return null;
  }

  @Nullable
  public String getFilename() {
    return myFile.getName();
  }

  public Integer getId() {
    return null;
  }

  public String getMimeType() {
    return myMimeType;
  }

  public String getExpectedSizeText() {
    return mySize;
  }

  public String getUser() {
    return null;
  }

  public long resolveOrCreate(UserChanges changes) throws AttachmentSaveException {
    final String localPlacementFileName = AttachmentUtils.makeFileCopyForUpload(changes, myFile);
    return create(
      changes.getCreator().createItem(),
      changes.getCreator().getItem(),
      changes.getConnectionItem(),
      localPlacementFileName);
  }

  public long create(ItemVersionCreator creator, long master, long connection, String localFileName) {
    creator.setValue(DBAttribute.TYPE, AttachmentsLink.typeAttachment);
    creator.setValue(SyncAttributes.CONNECTION, connection);
    creator.setValue(AttachmentsLink.attrMaster, master);
    creator.setValue(AttachmentsLink.attrDescription, getDescription());
    creator.setValue(AttachmentsLink.attrFileName, getFilename());
    creator.setValue(AttachmentsLink.attrMimeType, getMimeType());
    creator.setValue(AttachmentsLink.attrSize, getExpectedSizeText());
    creator.setValue(AttachmentsLink.attrLocalPath, localFileName);
    return creator.getItem();
  }

  public long create(EditDrain drain, ItemWrapper masterItem, String localFileName) {
    Connection conn = masterItem.getConnection();
    return create(drain.createItem(), masterItem.getItem(), conn != null ? conn.getConnectionItem() : 0L, localFileName);
  }

  public File getFileInOriginalPlace() {
    return myFile;
  }

  @Nullable
  public File getFileForUpload() {
    return null;
  }

  public long getExpectedSize() {
    return mySizeLong;
  }
}
