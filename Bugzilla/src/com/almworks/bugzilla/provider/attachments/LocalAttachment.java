package com.almworks.bugzilla.provider.attachments;

import com.almworks.api.application.UserChanges;
import com.almworks.util.files.FileUtil;
import org.jetbrains.annotations.*;

import java.io.File;
import java.util.Date;

public class LocalAttachment extends AbstractAttachmentInfo {
  private final long myItem;
  private final String myMimeType;
  private final String mySize;
  private final String myFileName;
  private final File myLocalFile;

  private long myExpectedSize;

  public LocalAttachment(long item, String description, String mimeType, String size, String fileName,
    File localFile)
  {
    super(description);
    myItem = item;
    myMimeType = mimeType;
    mySize = size;
    myFileName = fileName;
    myLocalFile = localFile;
  }

  public long resolveOrCreate(UserChanges changes) {
    return myItem;
  }

  public String getUser() {
    return null;
  }

  public Date getDate() {
    return null;
  }

  @Nullable
  public String getFilename() {
    return myFileName;
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

  public long getItem() {
    return myItem;
  }

  public File getFileForUpload() {
    return myLocalFile;
  }

  public long getExpectedSize() {
    long expectedSize = myExpectedSize;
    if (expectedSize >= 0)
      return expectedSize;
    myExpectedSize = FileUtil.getSizeFromString(mySize);
    return myExpectedSize;
  }
}
