package com.almworks.engine.gui.attachments;

import com.almworks.api.application.UserChanges;
import com.almworks.api.misc.WorkArea;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.files.FileActions;
import com.almworks.util.files.FileUtil;
import com.almworks.util.threads.ThreadAWT;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.actions.AnAction;
import org.almworks.util.Log;
import org.almworks.util.RuntimeInterruptedException;
import util.external.UID;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

public class AttachmentUtils {
  static final MessageFormat CANNOT_WRITE_FILE =
    new MessageFormat("<html><body>Cannot write {0}<br>Error: {1}</body></html>");

  public static AnAction createOpenWithAction(final File file, final Component dialogOwner) {
    return new FileDataAction("&Open With\u2026") {
      protected void perform(FileData data) {
        FileActions.openAs(file, dialogOwner);
      }
    };
  }

  public static AnAction createOpenContainingFolderAction(final File file, final Component owner) {
    return new FileDataAction(FileActions.OPEN_FOLDER_TITLE) {
      protected void perform(FileData data) {
        FileActions.openContainingFolder(file, owner);
      }
    };
  }

  public static AnAction createSaveAsAction(final File file, final Component owner, final Configuration config) {
    return new FileDataAction("&Save As\u2026") {
      protected void perform(FileData data) {
        saveAs(file, owner, data, config);
      }
    };
  }

  public static void saveAs(File file, final Component owner, final FileData data, Configuration config) {
    final File target = AttachmentChooserSaveAs.show(file, owner, config);
    if (target != null) {
      ThreadGate.LONG.execute(new Runnable() {
        public void run() {
          try {
            FileUtil.writeFile(target, data.getBytesInternal());
          } catch (final IOException e) {
            ThreadGate.AWT.execute(new ReportErrorRunnable(target, e, owner));
          }
        }
      });
    }
  }


  @ThreadAWT
  public static void saveAs(final File file, final Component owner, Configuration config) {
    Threads.assertAWTThread();
    final File target = AttachmentChooserSaveAs.show(file, owner, config);
    if (target != null) {
      ThreadGate.LONG.execute(new Runnable() {
        public void run() {
          try {
            FileUtil.copyFile(file, target, true);
          } catch (final IOException e) {
            ThreadGate.AWT.execute(new ReportErrorRunnable(target, e, owner));
          }
        }
      });
    }
  }

  public static void open(File file, Component owner) {
    FileActions.openFile(file, owner);
  }

  public static String makeFileCopyForUpload(UserChanges changes, File originalFile) throws AttachmentSaveException {
    return makeFileCopyForUpload(changes.getActor(WorkArea.APPLICATION_WORK_AREA), originalFile);
  }

  public static String makeFileCopyForUpload(WorkArea workArea, File originalFile) throws AttachmentSaveException {
    File uploadDir = workArea.getUploadDir();
    assert uploadDir != null;
    String name = originalFile.getName();
    String storeName = name;

    if (!originalFile.isFile()) {
      assert false : originalFile;
      return storeName;
    }

    File originalFileDir = originalFile.getAbsoluteFile().getParentFile();
    if (originalFileDir != null && originalFileDir.equals(uploadDir.getAbsoluteFile())) {
      // already in "upload" dir
      return storeName;
    }

    File file = new File(uploadDir, name);
    if (file.exists()) {
      int i;
      int tries = 10;
      for (i = 0; i < tries; i++) {
        String subdir = new UID().toString() + ".tmp";
        File dir = new File(uploadDir, subdir);
        if (!dir.exists()) {
          dir.mkdir();
          file = new File(dir, name);
          storeName = subdir + "/" + name;
          if (!file.exists())
            break;
        }
      }
      if (i == tries)
        throw new AttachmentSaveException("cannot create temporary space in folder " + uploadDir);
    }
    try {
      // todo this may take time! maybe have some kind of progress bar
      FileUtil.copyFile(originalFile, file);
    } catch (IOException e) {
      throw new AttachmentSaveException("cannot copy attached file to a temporary folder " + file.getParent());
    }
    return storeName;
  }

  public static void deleteUploadFile(final File file) {
    try {
      boolean success = FileUtil.deleteFile(file, true);
      if (!success) {
        Log.warn("cannot remove file " + file);
      } else {
        // see if we need to remove temp dir
        File dir = file.getParentFile();
        if (dir != null && dir.isDirectory() && dir.getName().endsWith(".tmp")) {
          FileUtil.deleteFile(dir, false);
        }
      }
    } catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
  }

  private static class ReportErrorRunnable implements Runnable {
    private final File myTarget;
    private final IOException myE;
    private final Component myOwner;

    public ReportErrorRunnable(File target, IOException e, Component owner) {
      myTarget = target;
      myE = e;
      myOwner = owner;
    }

    public void run() {
      String message = CANNOT_WRITE_FILE.format(new Object[] {myTarget.getAbsolutePath(), myE.getMessage()});
      JOptionPane.showMessageDialog(myOwner, message, "Save Error", JOptionPane.ERROR_MESSAGE);
    }
  }
}
