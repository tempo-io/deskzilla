package com.almworks.bugzilla.provider.attachments;

import com.almworks.api.application.Attachment;
import com.almworks.api.application.UserChanges;
import com.almworks.api.download.DownloadedFile;
import com.almworks.engine.gui.AttachmentProperty;
import com.almworks.engine.gui.attachments.AttachmentSaveException;
import com.almworks.util.components.CanvasRenderable;
import org.jetbrains.annotations.*;

import java.util.Date;

public abstract class AttachmentInfo extends Attachment implements CanvasRenderable, Comparable<AttachmentInfo> {
  public static final DescriptionProperty DESCRIPTION = new DescriptionProperty();

  public abstract long resolveOrCreate(UserChanges changes) throws AttachmentSaveException;

  public abstract String getDescription();

  public abstract Date getDate();

  public abstract Integer getId();

  public abstract boolean isLocal();

  public String toSearchableString() {
    String description = getDescription();
    String s = super.toSearchableString();
    return description == null ? s : s + " " + description;
  }

  private static class DescriptionProperty extends AttachmentProperty<AttachmentInfo, String> {
    public DescriptionProperty() {
      super("Description");
    }

    public String getColumnValue(@NotNull AttachmentInfo attachment, @Nullable DownloadedFile downloadedFile) {
      return attachment.getDescription();
    }

    public String getStringValue(@NotNull AttachmentInfo attachment, DownloadedFile downloadedFile) {
      return attachment.getDescription();
    }
  }
}
