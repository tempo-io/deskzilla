package com.almworks.engine.gui.attachments;

import com.almworks.api.application.Attachment;
import com.almworks.api.download.*;
import com.almworks.api.gui.*;
import com.almworks.spi.provider.ConnectionContext;
import com.almworks.util.Env;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.files.FileActions;
import com.almworks.util.files.FileUtil;
import com.almworks.util.progress.ProgressActivityFormat;
import com.almworks.util.progress.ProgressSource;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.GlobalColors;
import com.almworks.util.ui.UIComponentWrapper;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static com.almworks.api.download.DownloadedFile.State.*;

public class AttachmentsControllerUtil {
  public static void initiateDownload(DownloadOwner owner, Attachment attachment) {
    String url = attachment.getUrl();
    if (url == null) {
      assert false : attachment;
      return;
    }
    DownloadManager downloadManager = Context.require(DownloadManager.ROLE);
    downloadManager.initiateDownload(url, attachment.createDownloadRequest(), true, false);
  }

  public static void initiateDownload(ConnectionContext context, Attachment attachment) {
    if (context == null) {
      assert false : attachment;
      return;
    }
    initiateDownload(context.getDownloadOwner(), attachment);
  }

  public static void viewAttachment(File file, String mimeType, Attachment attachment, Configuration saveConfig, Component owner) {
    viewFile(file, mimeType, saveConfig, owner, getTitle(attachment), getDescription(attachment));
  }

  public static void viewFile(File file, String mimeType, Configuration saveConfig, Component owner, String title,
    String description)
  {
    if (!owner.isDisplayable()) owner = null;
    if (isGoodFile(file)) {
      if (AttachmentContent.isFileShowable(file, mimeType) || !Env.isWindows()) {
        AttachmentDisplayWindow.showFile(file, mimeType, title, description, saveConfig);
      } else {
        FileActions.openFile(file, owner);
      }
    }
  }

  public static void viewAttachment(DownloadedFile dFile, Attachment attachment, Configuration saveConfig, Component parentComponent) {
    viewAttachment(dFile.getFile(), dFile.getMimeType(), attachment, saveConfig, parentComponent);
  }

  public static boolean isGoodFile(File file) {
    return file != null && file.isFile() && file.canRead();
  }

  public static String getTitle(Attachment attachment) {
    String fileName = attachment.getFilename();
    return fileName == null ? "Attachment" : fileName;
  }

  public static String getDescription(Attachment attachment) {
    String date = attachment.getDateString();
    String user = attachment.getUser();
    StringBuffer sb = new StringBuffer(getTitle(attachment));
    if (user != null || date != null)
      sb.append(", uploaded");
    if (date != null)
      sb.append(" ").append(date);
    if (user != null)
      sb.append(" by ").append(user);
    return sb.toString();
  }

  public static void downloadAndShowAttachment(AttachmentsController controller, Attachment attachment, @NotNull Component parentComponent, Configuration viewConfig) {
    if (attachment.isLocal()) {
      File uploadFile = attachment.getFileForUpload();
      if (AttachmentsControllerUtil.isGoodFile(uploadFile)) {
        assert uploadFile != null;
        viewAttachment(uploadFile, FileUtil.guessMimeType(uploadFile.getName()), attachment, viewConfig, parentComponent);
      }
    } else {
      DownloadedFile dfile = controller.getDownloadedFile(attachment);
      if (isDownloadNeeded(dfile)) {
        controller.initiateDownload(attachment);
        waitDownloadedAndView(true, attachment, viewConfig, parentComponent);
      } else {
        if (dfile != null) {
          DownloadedFile.State state = dfile.getState();
          if (state != READY) waitDownloadedAndView(false, attachment, viewConfig, parentComponent);
          else viewAttachment(dfile, attachment, viewConfig, parentComponent);
        }
      }
    }
  }

  private static void waitDownloadedAndView(boolean canCancel, final Attachment attachment, final Configuration viewConfig, final Component parentComponent) {
    String url = attachment.getUrl();
    if (url == null) {
      assert false : attachment;
      return;
    }
    DownloadManager downloadManager = Context.require(DownloadManager.ROLE);
    DialogManager dm = Context.require(DialogManager.ROLE);
    waitDownloadAndView(dm, downloadManager, canCancel, url, getTitle(attachment), getDescription(attachment), viewConfig, parentComponent);
  }

  public static void waitDownloadAndView(DialogManager dm, DownloadManager downloadManager, boolean canCancel, String url, final String title, final String description, final Configuration viewConfig, final Component parentComponent) {
    FileDownloadListener.Tracker.perform(downloadManager, url, ThreadGate.AWT,
      new DownloadProgress(dm, viewConfig, parentComponent, title, description, downloadManager, url));
  }

  public static boolean isDownloadNeeded(DownloadedFile dfile) {
    if (dfile == null) return true;
    DownloadedFile.State state = dfile.getState();
    return state == null || state == DOWNLOAD_ERROR || state == LOST || state == UNKNOWN;
  }

  public static void registerActions(ActionRegistry registry) {
    registry.registerAction(MainMenu.Attachments.DOWNLOAD_ALL, DownloadAllAttachmentsAction.INSTANCE);
    registry.registerAction(MainMenu.Attachments.DOWNLOAD, DownloadAttachmentsAction.INSTANCE);
    registry.registerAction(MainMenu.Attachments.SAVE_AS, SaveAttachmentAction.INSTANCE);
    registry.registerAction(MainMenu.Attachments.VIEW, DownloadAndViewAttachmentAction.INSTANCE);
    registry.registerAction(MainMenu.Attachments.COPY_FILE_URL, CopyFileURLAction.INSTANCE);
    registry.registerAction(MainMenu.Attachments.SAVE_ALL, SaveAllAttachmentsAction.INSTANCE);
    registry.registerAction(MainMenu.Attachments.COPY_FILE_PATH, CopyFileLocationAction.INSTANCE);
    AttachmentFileAction.register(registry);

  }

  private static class DownloadProgress implements FileDownloadListener, AnActionListener {
    private final DialogManager myDialogs;
    private final Configuration myViewConfig;
    private final Component myParentComponent;
    private final String myTitle;
    private final String myDescription;
    private final DownloadManager myDownload;
    @Nullable private final String myUrl;
    private DownloadProgressForm myVisibleForm = null;
    private boolean myProgressShown = false;
    private DialogBuilder myBuilder;

    public DownloadProgress(DialogManager dialogs, Configuration viewConfig, Component parentComponent, String title,
      String description, @Nullable DownloadManager dm, @Nullable String url) {
      myDialogs = dialogs;
      myViewConfig = viewConfig;
      myParentComponent = parentComponent;
      myTitle = title;
      myDescription = description;
      myDownload = dm;
      myUrl = url;
    }

    public void onDownloadStatus(DownloadedFile dFile) {
      Threads.assertAWTThread();
      if (dFile.getState() == READY) {
        closeProgress();
        viewFile(dFile.getFile(), dFile.getMimeType(), myViewConfig, myParentComponent, myTitle, myDescription);
      } else if (dFile.getState() == DOWNLOAD_ERROR) showError(dFile.getLastDownloadError());
      else showProgress(dFile);
    }

    private void showProgress(DownloadedFile dFile) {
      DownloadProgressForm content = getVisibleForm();
      if (content == null) return;
      DownloadedFile.State state = dFile.getState();
      content.setState(DownloadedFile.State.getStateString(state));
      if (state == DOWNLOADING) content.setProgress(dFile.getDownloadProgressSource());
      else content.setProgress(null);
    }

    private DownloadProgressForm getVisibleForm() {
      if (myVisibleForm == null && !myProgressShown) {
        myProgressShown = true;
        showForm();
      }
      return myVisibleForm;
    }

    private DownloadProgressForm showForm() {
      if (myVisibleForm != null) return myVisibleForm;
      myVisibleForm = new DownloadProgressForm();
      myBuilder = myDialogs.createBuilder("attachmentDownloadProgress");
      myBuilder.setTitle("Download: " + myTitle);
      myBuilder.setContent(myVisibleForm);
      if (myDownload != null && myUrl != null) {
        myBuilder.setCancelAction("Cancel");
        myBuilder.addCancelListener(this);
      } else myBuilder.setCancelAction("Close");
      myBuilder.showWindow();
      return myVisibleForm;
    }

    private void showError(String error) {
      DownloadProgressForm form = showForm();
      form.setError(error);
    }

    private void closeProgress() {
      if (myBuilder == null) return;
      try {
        myBuilder.closeWindow();
      } catch (CantPerformException e) {
        Log.error(e);
      } finally {
        myBuilder = null;
        myVisibleForm = null;
      }
    }

    public void perform(ActionContext context) throws CantPerformException {
      myDownload.cancelDownload(myUrl);
    }
  }

  private static class DownloadProgressForm implements UIComponentWrapper, ChangeListener {
    private JPanel myWholePanel;
    private JProgressBar myProgressBar;
    private JLabel myState;
    private JLabel myLastMessage;
    private ProgressSource myProgress;
    private String myError;

    public JComponent getComponent() {
      return myWholePanel;
    }

    public void dispose() {
      stopListenProgress();
    }

    public void setState(String state) {
      myState.setText(state);
    }

    public void setProgress(ProgressSource progress) {
      if (progress == myProgress) return;
      if (myProgress != null) stopListenProgress();
      if (progress != null) {
        myProgress = progress;
        myProgress.getModifiable().addAWTChangeListener(Lifespan.FOREVER, this);
        onChange();
      }
      myProgressBar.setVisible(myProgress != null);
      myLastMessage.setVisible(myProgress != null || myError != null);
    }

    private void stopListenProgress() {
      if (myProgress != null) {
        myProgress.getModifiable().removeChangeListener(this);
        myProgress = null;
      }
    }

    public void onChange() {
      if (myError != null) return;
      if (myProgress == null) return;
      myProgressBar.setValue((int) (myProgress.getProgress() * 100));
      myLastMessage.setText(ProgressActivityFormat.DEFAULT.format(myProgress.getActivity()));
    }

    public void setError(String error) {
      myError = error;
      myProgressBar.setVisible(false);
      myLastMessage.setText(myError);
      myLastMessage.setVisible(true);
      myLastMessage.setForeground(GlobalColors.ERROR_COLOR);
      myState.setText("Failed");
    }
  }
}
