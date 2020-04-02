package com.almworks.bugzilla.gui.attachments;

import com.almworks.bugzilla.BugzillaBook;
import com.almworks.bugzilla.provider.attachments.NewAttachment;
import com.almworks.engine.gui.attachments.AttachmentChooserOpen;
import com.almworks.engine.gui.attachments.AttachmentContent;
import com.almworks.util.advmodel.SelectionInListModel;
import com.almworks.util.components.*;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.config.ConfigAccessors;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.files.FileUtil;
import com.almworks.util.i18n.LText;
import com.almworks.util.images.Icons;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.threads.Bottleneck;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.*;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifecycle;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;
import java.util.*;

public class AttachmentEditor implements UIComponentWrapper {
  private static final String X = "Bugzilla.Attachments.Editor.";
  private static final LText NO_FILE_SPECIFIED = BugzillaBook.text(X + "NO_FILE_SPECIFIED", "No file specified.");
  private static final LText FILE_DOES_NOT_EXIST =
    BugzillaBook.text(X + "FILE_DOES_NOT_EXIST", "The specified file does not exist.");
  private static final LText NOT_A_FILE =
    BugzillaBook.text(X + "NOT_A_FILE", "The specified file is not a regular file.");
  private static final LText CANNOT_READ = BugzillaBook.text(X + "CANNOT_READ", "The specified file cannot be read.");
  private static final LText HIDE_PREVIEW = BugzillaBook.text(X + "HIDE_PREVIEW", "&Preview");
  private static final LText SHOW_PREVIEW = BugzillaBook.text(X + "SHOW_PREVIEW", "&Preview");
  private static final LText NEED_DESCRIPTION =
    BugzillaBook.text(X + "NEED_DESCRIPTION", "Please enter description for this attachment.");
  private static final String MIME_TYPE_CONFIG_KEY = "MimeTypes";

  private FieldWithMoreButton myFilename;
  private JTextField mySize;
  private JButton myPreviewButton;
  private JTextField myDescription;
  private JPanel myFormPanel;
  private JLabel myErrorLabel;

  private JTextField myFilenameField;
  private JPanel myWholePanel;

  private boolean myDisplayPreview = false;
  private JDialog myPreviewDialog;

  private final Lifecycle myPreviewLife = new Lifecycle();
  private ConfigAccessors.Bool myDisplayPreviewAccessor;
  private boolean mySettingPreviewLocation;
  private Configuration myConfig;
  private PlaceHolder myPreviewer;
  private final Bottleneck myPreviewBottleneck = new Bottleneck(700, ThreadGate.AWT, new Runnable() {
    public void run() {
      updatePreviewer();
    }
  });
  private File myLastPreviewedFile;
  private boolean mySkippedEmptyPath = false;
  private String myLastPreviewedMimeType;

  private AComboBox<String> myMimeType;

  private File myLastSelectedFile = new File("");

  private final Runnable myPreviewLocationSetClearer = new Runnable() {
    public void run() {
      mySettingPreviewLocation = false;
    }
  };

  public AttachmentEditor(Configuration config) {
    Threads.assertAWTThread();
    myConfig = config;
    BugzillaBook.replaceText(X + "Form.", myFormPanel);

    myFilenameField = (JTextField) myFilename.getField();
    setupWholePanel();
    setupFileField();
    setupPreview(config);
    setupMimeTypes(config);
    setupDescription();
    setupVisual();
    adjustToPlatform();

    myErrorLabel.setForeground(GlobalColors.ERROR_COLOR);
  }

  private void setupDescription() {
    myDescription.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void documentChanged(DocumentEvent e) {
        updateError();
      }
    });
  }

  /**
   * Tries to retrive from configuration list of defined mime-types
   * if not found gets default list and passes it to Combobox model
   *
   * @param config Configuration instance passed from initializer
   */
  private void setupMimeTypes(Configuration config) {
    List<String> mimeTypes = config.getAllSettings(MIME_TYPE_CONFIG_KEY);
    if (mimeTypes == null || mimeTypes.isEmpty())
      mimeTypes = getDefaultMimeTypes();
    else
      // Making collection modifiable
      mimeTypes = Collections15.arrayList(mimeTypes);
    Collections.sort(mimeTypes);
    myMimeType.setModel(SelectionInListModel.create(mimeTypes, null));
  }

  /**
   * Return default defined mime-type (run once per workspace)
   *
   * @return list of mime-types defined by default
   */
  private List<String> getDefaultMimeTypes() {
    List<String> types = Collections15.arrayList();
    types.add("text/plain");
    types.add("text/x-diff");
    types.add("text/html");
    types.add("image/png");
    types.add("image/gif");
    types.add("image/jpeg");
    types.add("application/octet-stream");
    types.add("application/xml");
    types.add("application/zip");
    types.add("application/rar");
    return types;
  }

  public void saveMimeTypes() {
    java.util.List<String> types = new ArrayList<String>(myMimeType.getModel().toList());
    String selectedType = Util.NN(myMimeType.getModel().getSelectedItem()).trim();
    if (selectedType.length() > 0 && !types.contains(selectedType))
      types.add(selectedType);
    myConfig.setSettings(AttachmentEditor.MIME_TYPE_CONFIG_KEY, types);
  }

  private void setupVisual() {
    myFilenameField.setName("file");
    UIUtil.setupLabelFor(myFormPanel);
    UIUtil.setLabelMnemonics(myFormPanel);
    myWholePanel.setBorder(new EmptyBorder(5, 5, 5, 5));
  }

  private void setupWholePanel() {
    final Runnable updater = new Runnable() {
      public void run() {
        updatePreview();
      }
    };
    myWholePanel = new JPanel(new SingleChildLayout(SingleChildLayout.CONTAINER)) {
      public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(updater);
      }

      public void removeNotify() {
        super.removeNotify();
        // need to run updatePreview() immediately so the removing events won't be triggered
        updater.run();
      }
    };
    myWholePanel.add(myFormPanel);
  }

  private void adjustToPlatform() {
    myErrorLabel.putClientProperty(UIUtil.SET_DEFAULT_LABEL_ALIGNMENT, false);
    UIUtil.setDefaultLabelAlignment(myWholePanel);
    Aqua.disableMnemonics(myWholePanel);
  }

  private void setupPreview(Configuration config) {
    myDisplayPreviewAccessor = ConfigAccessors.bool(config, "showPreview", true);
    myPreviewButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setDisplayPreview(!myDisplayPreviewAccessor.getBool());
      }
    });
    setDisplayPreview(myDisplayPreviewAccessor.getBool());
    myPreviewButton.setHorizontalTextPosition(SwingConstants.LEFT);
    updatePreviewButton();
  }

  private void updatePreviewButton() {
    boolean display = isPreviewDisplayable();
    String text = display ? HIDE_PREVIEW.format() : SHOW_PREVIEW.format();
    Icon icon = display ? Icons.DOUBLE_SIGN_ARROW_RIGHT : Icons.DOUBLE_SIGN_ARROW_LEFT;
    NameMnemonic.parseString(text).setToButton(myPreviewButton);
    myPreviewButton.setIcon(icon);
  }

  private void setDisplayPreview(boolean displayPreview) {
    myDisplayPreviewAccessor.setBool(displayPreview);
    myDisplayPreview = displayPreview;
    updatePreview();
  }

  private void updatePreview() {
    boolean display = isPreviewDisplayable();
    boolean isDisplaying = myPreviewDialog != null;
    if (display != isDisplaying) {
      updatePreviewButton();
      if (display)
        showPreview();
      else
        hidePreview();
    }
  }

  private void hidePreview() {
    myPreviewLife.cycle();
    if (myPreviewDialog != null)
      myPreviewDialog.dispose();
    myPreviewDialog = null;
    if (myPreviewer != null)
      myPreviewer.clear();
    myPreviewer = null;
    myLastPreviewedFile = null;
  }

  private void showPreview() {
    final Window mainWindow = SwingUtilities.getWindowAncestor(myPreviewButton);
    assert mainWindow != null;
    if (mainWindow == null)
      return;
    myPreviewLife.cycle();
    if (mainWindow instanceof JDialog) {
      myPreviewDialog = new JDialog((JDialog) mainWindow, "Preview");
    } else if (mainWindow instanceof JFrame) {
      myPreviewDialog = new JDialog((JFrame) mainWindow, "Preview");
    } else {
      assert false : mainWindow;
      return;
    }

    Lifespan lifespan = myPreviewLife.lifespan();
    lifespan.add(UIUtil.addWindowListener(myPreviewDialog, new WindowAdapter() {
      public void windowClosed(WindowEvent e) {
        setDisplayPreview(false);
      }
    }));

    Dimension preferredSize = new Dimension(300, 200);
    Configuration config = myConfig.getOrCreateSubset("PreviewSize");
    WindowUtil.setupWindow(lifespan, myPreviewDialog, config, false, preferredSize, false, null, null);

    setPreviewLocation(mainWindow, false);
    myPreviewDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    myPreviewer = createPreviewer(lifespan);
    myPreviewDialog.getContentPane().add(myPreviewer);
    myPreviewDialog.show();
    mainWindow.requestFocus();

    myPreviewBottleneck.run();

    ComponentAdapter moveListener = new ComponentAdapter() {
      public void componentMoved(ComponentEvent e) {
        setPreviewLocation(mainWindow, false);
      }

      public void componentResized(ComponentEvent e) {
        setPreviewLocation(mainWindow, false);
      }
    };
    lifespan.add(UIUtil.addComponentListener(mainWindow, moveListener));
    lifespan.add(UIUtil.addComponentListener(myWholePanel, moveListener));

    lifespan.add(UIUtil.addComponentListener(myPreviewDialog, new ComponentAdapter() {
      public void componentMoved(ComponentEvent e) {
        setPreviewLocation(mainWindow, true);
      }
    }));
  }

  private PlaceHolder createPreviewer(Lifespan previewDetach) {
    PlaceHolder placeHolder = new PlaceHolder();
    DocumentAdapter previewRunner = DocumentUtil.runOnChange(myPreviewBottleneck);
    DocumentUtil.addListener(previewDetach, myFilenameField.getDocument(), previewRunner);
    myMimeType.getModel().addSelectionChangeListener(previewDetach, myPreviewBottleneck);
    return placeHolder;
  }

  private void updatePreviewer() {
    if (myPreviewer == null)
      return;
    String path = getPath();
    String mimeType = Util.NN(myMimeType.getModel().getSelectedItem()).trim();
    File file = path == null ? null : new File(path);
    if (!Util.equals(file, myLastPreviewedFile) || !Util.equals(mimeType, myLastPreviewedMimeType)) {
      if (path == null || path.length() == 0) {
        // handle Document peculiarity - when replacing text, we access update with "", only then with the new text.
        if (!mySkippedEmptyPath) {
          mySkippedEmptyPath = true;
          myPreviewBottleneck.run();
          return;
        }
      }
      mySkippedEmptyPath = false;
      myLastPreviewedFile = file;
      myLastPreviewedMimeType = mimeType;
      AttachmentContent content = new AttachmentContent(file, mimeType);
      content.loadFile();
      myPreviewer.showThenDispose(content);
    }
  }


  private boolean isPreviewDisplayable() {
    return myDisplayPreview && myPreviewButton.isDisplayable();
  }

  private void setPreviewLocation(Window mainWindow, boolean previewDrives) {
    if (mySettingPreviewLocation)
      return;
    mySettingPreviewLocation = true;
    boolean affected = false;
    try {
      assert myPreviewDialog != null;
      if (myPreviewDialog == null)
        return;
      Rectangle bounds = mainWindow.getBounds();
      if (previewDrives) {
        Rectangle previewBounds = myPreviewDialog.getBounds();
        Point newLocation = new Point(previewBounds.x - bounds.width, previewBounds.y);
        if (!newLocation.equals(mainWindow.getLocation())) {
          mainWindow.setLocation(newLocation);
          affected = true;
        }
      } else {
        Point newLocation = new Point(bounds.x + bounds.width, bounds.y);
        if (!newLocation.equals(myPreviewDialog.getLocation())) {
          myPreviewDialog.setLocation(newLocation);
          affected = true;
        }
      }
    } finally {
      if (!affected)
        myPreviewLocationSetClearer.run();
      else
        SwingUtilities.invokeLater(myPreviewLocationSetClearer);
    }
  }

  private void setupFileField() {
    myFilename.setAction(new AnActionListener() {
      public void perform(ActionContext context) throws CantPerformException {
        File file = AttachmentChooserOpen.show(myWholePanel, myFilenameField.getText(),
          BugzillaAttachFileAction.MAX_FILE_LENGTH, myConfig);
        if (file != null)
          setFilename(file);
      }
    });

    myFilenameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void documentChanged(DocumentEvent e) {
        onFileUpdated();
      }
    });

    onFileUpdated();
  }

  private void onFileUpdated() {
    myLastSelectedFile = new File(Util.NN(getPath()));
    setSize(myLastSelectedFile);
    setMimeType(myLastSelectedFile);
    updateError();
  }

  private String getPath() {
    return myFilenameField.getText().trim();
  }

  private void updateError() {
    File file = myLastSelectedFile;
    if (file == null || file.getPath().trim().length() == 0)
      setError(NO_FILE_SPECIFIED.format());
    else if (!file.exists())
      setError(FILE_DOES_NOT_EXIST.format());
    else if (!file.isFile())
      setError(NOT_A_FILE.format());
    else if (!file.canRead())
      setError(CANNOT_READ.format());
    else if (getDescription().length() == 0)
      setError(NEED_DESCRIPTION.format());
    else
      setError("");
  }

  private void setError(String text) {
    String oldText = myErrorLabel.getText();
    if (!Util.equals(oldText, text)) {
      myErrorLabel.setText(text);
      myErrorLabel.setVisible(text != null && text.length() > 0);
    }
  }

  private void setMimeType(File file) {
    String mimeType;
    if (file == null) {
      // Actualy never call (yet), because method is private
      // and in caller File instantiated with not null string
      // Could be left for future safety
      mimeType = null;
    } else {
      mimeType = FileUtil.guessMimeType(file.getName());
      if (mimeType == null)
        // default binary data format
        mimeType = "application/octet-stream";
    }
    myMimeType.getModel().setSelectedItem(mimeType);
  }

  private void setSize(File file) {
    if (file != null && file.isFile()) {
      mySize.setText(FileUtil.getSizeString(file.length()));
    } else {
      mySize.setText("");
    }
  }

  private void setFilename(File file) {
    try {
      if (file == null) {
        myFilenameField.setText("");
      } else {
        String path = file.getAbsolutePath();
        myFilenameField.setText(path);
        Rectangle r = myFilenameField.modelToView(path.length());
        if (r != null)
          myFilenameField.scrollRectToVisible(r);
      }
    } catch (BadLocationException e) {
      // ignore
    }
  }


  public void selectFile(File attachment) {
    setFilename(attachment);
  }

  public JComponent getComponent() {
    return myWholePanel;
  }

  public void dispose() {

  }

  public void attach(UpdateRequest updateRequest) {
    updateRequest.updateOnChange(myFilenameField.getDocument());
    updateRequest.updateOnChange(myMimeType.getModel());
    updateRequest.updateOnChange(myDescription.getDocument());
  }

  public File getFile() {
    return new File(getPath());
  }

  public String getMimeType() {
    return Util.NN(myMimeType.getModel().getSelectedItem()).trim();
  }

  public String getDescription() {
    return Util.NN(myDescription.getText()).trim();
  }

  public NewAttachment extractAttachmentData() throws CantPerformException {
    File file = getFile();
    CantPerformException.ensure(file.isFile() && file.canRead());
    final String mimeType = getMimeType();
    CantPerformException.ensure(mimeType.indexOf('/') > 0);
    final String description = getDescription();
    CantPerformException.ensure(!description.isEmpty());
    final long length = file.length();
    return new NewAttachment(file, mimeType, length, FileUtil.getSizeString(length), description);
  }
}

