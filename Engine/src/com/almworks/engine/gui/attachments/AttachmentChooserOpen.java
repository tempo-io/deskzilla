package com.almworks.engine.gui.attachments;

import com.almworks.util.L;
import com.almworks.util.config.Configuration;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.MessageFormat;

public class AttachmentChooserOpen extends AttachmentChooserBase {
  private static final String TOO_LARGE_FILE = "The file is too large. The maximum attachment size is {0,number,#########} bytes.";
  private static final String TOO_LARGE_TITLE = L.dialog("File Is Too Large");
  private static final String ZERO_SIZE_FILE = "Cannot attach empty file.";
  private static final String ZERO_SIZE_TITLE = L.dialog("File Is Empty");

  private final String myInitialSelection;
  private final long myMaxFileLength;

  private final String myTitle;

  public AttachmentChooserOpen(Component owner, String initialSelection, long maxFileLength, boolean multiple,
    Configuration config) {
    super(owner, config);
    myInitialSelection = initialSelection;
    myMaxFileLength = maxFileLength;
    myTitle = multiple ? "Select Files to Attach" : "Select File to Attach";
  }

  protected String getTitle() {
    return myTitle;
  }

  protected int showChooser(Window owner) {
    return myChooser.showOpenDialog(owner);
  }

  protected void prepareDialog() {
    super.prepareDialog();
    if (myInitialSelection != null && myInitialSelection.length() > 0) {
      File file = new File(myInitialSelection);
      if (file.isFile()) {
        myChooser.setCurrentDirectory(file.getParentFile());
        myChooser.setSelectedFile(file);
        myChooser.ensureFileIsVisible(file);
      } else if (file.isDirectory()) {
        myChooser.setCurrentDirectory(file);
      }
    }
  }

  protected boolean approveSelection(File file) {
    if (file == null)
      return false;
    if (!(file.isFile() && file.canRead()))
      return false;
    long length = file.length();
    if (myMaxFileLength > 0 && length > myMaxFileLength) {
      JOptionPane.showMessageDialog(myChooser,
        MessageFormat.format(TOO_LARGE_FILE, myMaxFileLength),
        TOO_LARGE_TITLE, JOptionPane.ERROR_MESSAGE);
      return false;
    }
    if (length == 0) {
      JOptionPane.showMessageDialog(myChooser, ZERO_SIZE_FILE, ZERO_SIZE_TITLE, JOptionPane.ERROR_MESSAGE);
      return false;
    }
    return true;
  }

  public static File show(Component owner, String initialSelection, long maxFileLength, Configuration config) {
    AttachmentChooserOpen myChooser = new AttachmentChooserOpen(owner, initialSelection, maxFileLength, false, config);
    return myChooser.choose();
  }

  public static File[] showForMultiple(Component owner, long maxFileLength, Configuration config) {
    AttachmentChooserOpen myChooser = new AttachmentChooserOpen(owner, null, maxFileLength, true, config);
    return myChooser.chooseMultiple();
  }
}
