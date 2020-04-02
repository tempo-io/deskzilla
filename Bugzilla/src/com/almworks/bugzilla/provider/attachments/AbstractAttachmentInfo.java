package com.almworks.bugzilla.provider.attachments;

import com.almworks.api.download.DefaultDownloadOwner;
import com.almworks.api.download.DownloadOwner;
import com.almworks.bugzilla.BugzillaBook;
import com.almworks.util.collections.Containers;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.i18n.LText;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.text.DateFormat;
import java.util.Date;

public abstract class AbstractAttachmentInfo extends AttachmentInfo {
  private static final LText NO_DESCRIPTION =
    BugzillaBook.text("Bugzilla.AttachmentsView." + "no-description", "(No Description)");

  protected final String myDescription;
  private static final int MAX_RENDERED_LENGTH = 35;

  public AbstractAttachmentInfo(String description) {
    myDescription = description;
  }

  public String getDescription() {
    return Util.NN(myDescription);
  }

  public boolean isLocal() {
    return true;
  }

  public void renderOn(Canvas canvas, CellState state) {
    String description = myDescription;
    String text;
    if (description == null)
      text = NO_DESCRIPTION.format();
    else {
      text = description;
      if (text.length() > MAX_RENDERED_LENGTH) {
        canvas.setToolTipText(text);
        text = text.substring(0, MAX_RENDERED_LENGTH) + "...";
      }
    }
    canvas.appendText(text);
  }

  /**
   * Order:
   * 1. All committed attachments in order of their ids
   * 2. All attachments with items in order of their artifact key
   * 3. All other attachments in order of their description
   */
  public int compareTo(AttachmentInfo that) {
    Integer thisId = getId();
    Integer thatId = that.getId();
    if (thisId != null) {
      if (thatId != null)
        return thisId.compareTo(thatId);
      else
        return -1;
    }
    if (thatId != null)
      return 1;
    assert thisId == null && thatId == null;

    long thisArtifact = getItem();
    long thatArtifact = that.getItem();
    if (thisArtifact > 0) {
      if (thatArtifact > 0) {
        return Containers.compareLongs(thisArtifact, thatArtifact);
      } else {
        return -1;
      }
    }
    if (thatArtifact > 0)
      return 1;
    assert thisArtifact <= 0 && thatArtifact <= 0;

    return Util.NN(getDescription()).compareTo(Util.NN(that.getDescription()));
  }


  @Nullable
  public String getUrl() {
    return null;
  }

  @NotNull
  public DownloadOwner getDownloadOwner() {
    return DefaultDownloadOwner.INSTANCE;
  }

  public String getDateString() {
    Date date = getDate();
    if (date == null)
      return null;
    else
      return DateFormat.getDateInstance(DateFormat.SHORT).format(date);
  }

  public String getDownloadArgument() {
    return null;
  }

  public int getNumber() {
    return 0;
  }
}
