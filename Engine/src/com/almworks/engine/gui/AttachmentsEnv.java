package com.almworks.engine.gui;

import com.almworks.api.application.Attachment;
import com.almworks.api.download.DownloadedFile;
import com.almworks.spi.provider.ConnectionContext;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.threads.ThreadAWT;
import com.almworks.util.threads.ThreadSafe;
import com.almworks.util.ui.actions.DataRole;
import org.jetbrains.annotations.*;

public interface AttachmentsEnv {
  DataRole<Attachment> ATTACHMENT = DataRole.createRole(Attachment.class);

  @ThreadSafe
  @Nullable
  DownloadedFile getDownloadedFile(String url);

  @ThreadSafe
  @Nullable
  DownloadedFile getDownloadedFile(Attachment attachment);

  @ThreadSafe
  @Nullable
  ConnectionContext getContext();

  @ThreadAWT
  void repaintAttachment(String url);

  @ThreadAWT
  void revalidateAttachments();

  Modifiable getDownloadModifiable();

  @Nullable
  String getTooltipText(Attachment item);
}
