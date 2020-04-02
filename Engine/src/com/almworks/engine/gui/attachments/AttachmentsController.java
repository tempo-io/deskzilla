package com.almworks.engine.gui.attachments;

import com.almworks.api.application.Attachment;
import com.almworks.api.download.DownloadedFile;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.config.Configuration;
import com.almworks.util.ui.actions.DataRole;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.io.File;
import java.util.Collection;

public interface AttachmentsController {
  DataRole<AttachmentsController> ROLE = DataRole.createRole(AttachmentsController.class);

  /**
   * @return collection of all currently known attachments
   */
  Collection<? extends Attachment> getAttachments();

  /**
   * Downloads (if not downloaded yet) the attachment and shows it to a user
   * @param attachment to show. Need not appear in collection returned by {@link #getAttachments()}
   * @param parentComponent suggested parent for viewer window
   */
  void showAttachment(Attachment attachment, @Nullable Component parentComponent);

  /**
   * If attachment doesnt appear in {@link #getAttachments()} the file may be null or not reflect the actual attachment state.
   * @param attachment
   * @return
   */
  DownloadedFile getDownloadedFile(Attachment attachment);

  /**
   * @return modifiable fires change events when some attachments download status changes
   */
  Modifiable getDownloadedStatusModifiable();

  /**
   * @return modifiable fires change events when attachments collection ({@link #getAttachments()} ) changes:<br>
   * 1. Attachments set changes (added or removed)<br>
   * 2. Attachments updated
   */
  Modifiable getAllAttachmentsModifiable();

  /**
   * Initiates download of the attachment
   */
  void initiateDownload(Attachment attachment);

  void saveAs(File file, Component component);

  Configuration getViewConfig();
}
