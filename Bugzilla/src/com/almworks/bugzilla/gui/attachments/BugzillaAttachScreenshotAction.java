package com.almworks.bugzilla.gui.attachments;

import com.almworks.api.actions.AttachScreenshotAction;
import com.almworks.bugzilla.provider.attachments.NewAttachment;
import com.almworks.items.sync.EditDrain;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.files.FileUtil;
import com.almworks.util.ui.actions.AnAction;
import org.almworks.util.Util;

import java.io.File;

public class BugzillaAttachScreenshotAction extends AttachScreenshotAction {
  public static final AnAction INSTANCE = new BugzillaAttachScreenshotAction();

  protected void makeAttach(long bug, File file, String description, EditDrain drain) {
    long length = file.length();
    final NewAttachment attachment = new NewAttachment(file, "image/png", length, FileUtil.getSizeString(length), description);
    attachment.create(drain.createItem(), bug, Util.NN(drain.getReader().getValue(bug, SyncAttributes.CONNECTION), 0L), file.getName());
  }
}
