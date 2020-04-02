package com.almworks.bugzilla.provider.attachments;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.UserChanges;
import com.almworks.api.download.*;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.provider.BugzillaAccessPurpose;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import com.almworks.util.files.FileUtil;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.io.File;
import java.util.Date;

public class FinalAttachment extends AbstractAttachmentInfo {
  private final Integer myId;
  private final ItemKey mySubmitter;
  private final Date myDate;
  private final String myMimeType;
  private final String mySize;
  private final String myFileName;
  private final long myItem;
  private final BugzillaContext myContext;

  private String myUrl;
  private long myExpectedSize = -1;

  public FinalAttachment(long item, Integer id, String description, ItemKey submitter, Date date,
    String mimeType, String size, String fileName, BugzillaContext context)
  {
    super(description);

    assert item > 0;
    assert id != null;
    assert description != null;

    myItem = item;
    myId = id;
    mySubmitter = submitter;
    myDate = date;
    myMimeType = mimeType;
    mySize = size;
    myFileName = fileName;
    myContext = context;
  }

  public String getUser() {
    return mySubmitter == null ? "" : mySubmitter.getDisplayName();
  }

  @Nullable
  public Date getDate() {
    return myDate;
  }

  public String getFilename() {
    return myFileName;
  }

  public Integer getId() {
    return myId;
  }

  public String getMimeType() {
    return myMimeType;
  }

  public String getExpectedSizeText() {
    return mySize;
  }

  public long resolveOrCreate(UserChanges changes) {
    return myItem;
  }

  public long getItem() {
    return myItem;
  }

  public boolean isLocal() {
    return false;
  }

  @NotNull
  public DownloadOwner getDownloadOwner() {
    return myContext == null ? DefaultDownloadOwner.INSTANCE : myContext.getDownloadOwner();
  }

  @Nullable
  public String getUrl() {
    BugzillaContext context = myContext;
    if (context == null || myId == null)
      return null;
    if (myUrl == null) {
      Integer id = myId;
      myUrl = getAttachmentUrl(context, id);
    }
    return myUrl;
  }

  public static String getAttachmentUrl(BugzillaContext context, Integer id) {
    try {
      BugzillaIntegration integration = context.getIntegration(BugzillaAccessPurpose.IMMEDIATE_DOWNLOAD);
      return integration.getDownloadAttachmentURL(id);
    } catch (ConnectionNotConfiguredException e) {
      Log.debug(e);
    }
    return null;
  }

  public long getExpectedSize() {
    long expectedSize = myExpectedSize;
    if (expectedSize >= 0)
      return expectedSize;
    myExpectedSize = FileUtil.getSizeFromString(mySize);
    return myExpectedSize;
  }

  public String getDownloadArgument() {
    return String.valueOf(myId);
  }

  @Nullable
  public File getFileForUpload() {
    return null;
  }

  public int getNumber() {
    return myId == null ? 0 : myId;
  }

  public static DownloadRequest createDownloadRequest(DownloadOwner downloadOwner, int id, @Nullable String suggestedName,
    long expectedSize) {
    String strId = String.valueOf(id);
    if (suggestedName == null) suggestedName = "attachment_" + strId;
    return new DownloadRequest(downloadOwner, strId, suggestedName, expectedSize);
  }
}
