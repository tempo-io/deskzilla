package com.almworks.bugzilla.gui.attachments;

import com.almworks.api.actions.BaseAttachFileAction;
import com.almworks.api.actions.UploadOnSuccess;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.edit.EditLifecycle;
import com.almworks.api.misc.WorkArea;
import com.almworks.bugzilla.provider.attachments.NewAttachment;
import com.almworks.engine.gui.attachments.AttachmentSaveException;
import com.almworks.engine.gui.attachments.AttachmentUtils;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.sync.EditCommit;
import com.almworks.items.sync.EditDrain;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.util.config.Configuration;
import com.almworks.util.ui.actions.*;
import util.concurrent.SynchronizedBoolean;

import java.io.File;
import java.util.Arrays;

public class BugzillaAttachFileAction extends BaseAttachFileAction<AttachmentEditor> {
  public static final int MAX_FILE_LENGTH = 50000000;
  public static final AnAction INSTANCE = new BugzillaAttachFileAction();

  private BugzillaAttachFileAction() {
    super(false, MAX_FILE_LENGTH);
  }

  protected SimpleAction createAttachAction(AttachmentEditor editor, ItemWrapper masterItem, SynchronizedBoolean uploadImmediately) {
    return new AttachAction(editor, masterItem, uploadImmediately);
  }

  protected AttachmentEditor createEditor(Configuration config, File[] attachments, ItemWrapper item) {
    assert attachments.length == 1 : this + " " + Arrays.toString(attachments);
    final AttachmentEditor editor = new AttachmentEditor(config);
    editor.selectFile(attachments[0]);
    return editor;
  }

  private static class AttachAction extends SimpleAction {
    private final AttachmentEditor myEditor;
    private final ItemWrapper myMasterItem;
    private final SynchronizedBoolean myUploadImmediately;

    AttachAction(AttachmentEditor editor, ItemWrapper masterItem, SynchronizedBoolean uploadImmediately) {
      super("Attach");
      myEditor = editor;
      myMasterItem = masterItem;
      myUploadImmediately = uploadImmediately;
      watchModifiableRole(EditLifecycle.MODIFIABLE);
    }

    protected void customUpdate(UpdateContext context) throws CantPerformException {
      myEditor.attach(context.getUpdateRequest());
      myEditor.extractAttachmentData();
      boolean duringCommit = context.getSourceObject(EditLifecycle.ROLE).isDuringCommit();
      myEditor.getComponent().setEnabled(!duringCommit);
      context.setEnabled(!duringCommit);
    }

    protected void doPerform(final ActionContext context) throws CantPerformException {
      myEditor.saveMimeTypes();
      final NewAttachment data = myEditor.extractAttachmentData();
      EditLifecycle editLife = context.getSourceObject(EditLifecycle.ROLE);
      try {
        WorkArea workArea = myMasterItem.services().getActor(WorkArea.APPLICATION_WORK_AREA);
        final String localFileName = AttachmentUtils.makeFileCopyForUpload(workArea, data.getFileInOriginalPlace());
        AggregatingEditCommit commit = new AggregatingEditCommit();
        commit.addProcedure(null, new EditCommit.Adapter() {
          @Override
          public void performCommit(EditDrain drain) throws DBOperationCancelledException {
            data.create(drain, myMasterItem, localFileName);
          }
        });
        if (myUploadImmediately.get()) commit.addProcedure(null, UploadOnSuccess.create(myMasterItem));
        editLife.commit(context, commit);
      } catch (AttachmentSaveException e) {
        throw new CantPerformExceptionExplained("Cannot attach file: " + e.getMessage());
      }
    }
  }
}
