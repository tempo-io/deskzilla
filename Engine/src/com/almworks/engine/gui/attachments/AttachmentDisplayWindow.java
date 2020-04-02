package com.almworks.engine.gui.attachments;

import com.almworks.api.gui.DialogBuilder;
import com.almworks.api.gui.DialogManager;
import com.almworks.util.Terms;
import com.almworks.util.components.AActionButton;
import com.almworks.util.components.ALabel;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.Context;
import com.almworks.util.files.FileActions;
import com.almworks.util.i18n.Local;
import com.almworks.util.ui.InlineLayout;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Util;

import javax.swing.*;
import java.awt.*;
import java.io.File;

class AttachmentDisplayWindow {

  private final DialogBuilder myBuilder;
  private final File myFile;
  private final String myMimeType;
  private final String myDescription;

  private JPanel myTopPanel;
  private JPanel myWholePanel;
  private AttachmentContent myContent;

  private SimpleProvider myDataProvider;
  private final Configuration mySaveConfig;

  public AttachmentDisplayWindow(File file, String mimeType, String title, String description, Configuration saveConfig) {
    myFile = file;
    myMimeType = mimeType;
    myDescription = description;
    mySaveConfig = saveConfig;

    DialogManager manager = Context.require(DialogManager.ROLE);
    myBuilder = manager.createBuilder("showFile_" + Util.NN(mimeType, "notype"));
    myBuilder.setTitle(title + " - " + Local.text(Terms.key_Deskzilla));
    myBuilder.setModal(false);
    myBuilder.setCancelAction("Close Window");
    myBuilder.setContent(createContent());
    myBuilder.setPreferredSize(new Dimension(700, 560));
  }

  public static void showFile(File file, String mimeType, String title, String description, Configuration saveConfig) {
    AttachmentDisplayWindow window = new AttachmentDisplayWindow(file, mimeType, title, description, saveConfig);
    window.show();
  }

  private JComponent createContent() {
    ALabel description = new ALabel(myDescription);
    description.setHorizontalAlignment(SwingConstants.LEADING);
    UIUtil.adjustFont(description, 1.05F, Font.BOLD, true);

    myTopPanel = new JPanel(UIUtil.createBorderLayout());
    myTopPanel.add(description, BorderLayout.CENTER);
    myTopPanel.add(createToolbar(), BorderLayout.SOUTH);

    myContent = new AttachmentContent(myFile, myMimeType);

    myWholePanel = new JPanel(UIUtil.createBorderLayout());
    myWholePanel.add(myTopPanel, BorderLayout.NORTH);
    myWholePanel.add(myContent.getComponent(), BorderLayout.CENTER);

    myDataProvider = new SimpleProvider(FileData.FILE_DATA);
    DataProvider.DATA_PROVIDER.putClientValue(myWholePanel, myDataProvider);

    return myWholePanel;
  }

  private Component createToolbar() {
    JPanel toolbar = new JPanel(new InlineLayout(InlineLayout.HORISONTAL, 5));
    toolbar.add(createButton(AttachmentUtils.createSaveAsAction(myFile, myWholePanel, mySaveConfig)));
    toolbar.add(createButton(createCopyToClipboardAction()));
    if (FileActions.isSupported(FileActions.Action.OPEN_AS)) {
      toolbar.add(createButton(AttachmentUtils.createOpenWithAction(myFile, myWholePanel)));
    }
    if (FileActions.isSupported(FileActions.Action.OPEN_CONTAINING_FOLDER)) {
      toolbar.add(createButton(AttachmentUtils.createOpenContainingFolderAction(myFile, myWholePanel)));
    }
    return toolbar;
  }

  public AnAction createCopyToClipboardAction() {
    return new FileDataAction("Copy to &Clipboard") {
      protected void perform(FileData data) {
        myContent.copyToClipboard();
      }
    };
  }

  private AActionButton createButton(AnAction action) {
    AActionButton button = new AActionButton(action);
    button.setContextComponent(myWholePanel);
    return button;
  }


  public void show() {
    myContent.setListener(new AttachmentContent.Listener() {
      public void onLoaded(FileData data) {
        myWholePanel.repaint();
        myWholePanel.revalidate();
        if (data == null)
          myDataProvider.removeAllData();
        else
          myDataProvider.setSingleData(FileData.FILE_DATA, data);
      }
    });
    myContent.loadFile();
    myBuilder.showWindow();
  }
}
