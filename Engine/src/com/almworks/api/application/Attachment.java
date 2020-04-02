package com.almworks.api.application;

import com.almworks.api.download.DownloadOwner;
import com.almworks.api.download.DownloadRequest;
import com.almworks.util.commons.Condition;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.io.File;
import java.util.Date;

public abstract class Attachment implements UiItem {
  public static final Condition<Attachment> LOCAL = new Condition<Attachment>() {
    @Override
    public boolean isAccepted(Attachment value) {
      return value != null && value.isLocal();
    }
  };
  /**
   * @return a number associated with this attachment. it is used to order attachments.
   * return <=0 if number is not known.
   */
  public abstract int getNumber();

  @NotNull
  public abstract DownloadOwner getDownloadOwner();

  @Nullable
  public abstract String getDownloadArgument();

  @Nullable
  public abstract String getUrl();

  @Nullable
  public abstract String getFilename();

  public abstract long getExpectedSize();


  /**
   * @return file in upload dir that will be uploaded
   *
   */
  @Nullable
  public abstract File getFileForUpload();

  public abstract boolean isLocal();

  @Nullable
  public abstract String getMimeType();

  @Nullable
  public abstract String getExpectedSizeText();

  public abstract String getUser();

  public abstract String getDateString();

  @Nullable
  public abstract Date getDate();

  public DownloadRequest createDownloadRequest() {
    String filename = Util.NN(getFilename(), "attachment.dat");
    return new DownloadRequest(getDownloadOwner(), getDownloadArgument(), filename, getExpectedSize());
  }

  public String toSearchableString() {
    String filename = getFilename();
    return filename == null ? "" : filename;
  }
}
