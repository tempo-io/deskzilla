package com.almworks.engine.gui.attachments;

import com.almworks.api.application.Attachment;
import com.almworks.api.download.DownloadedFile;
import com.almworks.api.image.*;
import com.almworks.engine.gui.AttachmentProperty;
import com.almworks.engine.gui.AttachmentsEnv;
import com.almworks.util.components.ThumbnailViewCellGeometry;
import com.almworks.util.components.ThumbnailViewUI;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.files.FileUtil;
import com.almworks.util.images.ImageUtil;
import com.almworks.util.progress.ProgressData;
import com.almworks.util.threads.*;
import com.almworks.util.ui.ColorUtil;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.dnd.DndHack;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

class AttachmentThumbnailUI<T extends Attachment>
  implements ThumbnailViewUI<T>, ThumbnailSourceFactory, ThumbnailReadyNotificator
{
  private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
  private static final int MAX_THUMB_FIT_HEIGHT = 300;

  private final ThumbnailViewCellGeometry myGeometry;
  private final AttachmentsEnv myEnv;

  private final CellRendererPane myRendererPane = new CellRendererPane();
  private final JProgressBar myProgressBar = new JProgressBar() {
    public boolean isShowing() {
      return true;
    }
  };
  @Nullable
  private final Thumbnailer myThumbnailer;

  private static final String ID_SEPARATOR = "##";
  private static final Dimension BIG_THUMB = new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);

  private boolean myRemakeGeometry = false;
  private static final String UPLOAD_PREFIX = ID_SEPARATOR + "upload" + ID_SEPARATOR;

  private AttachmentProperty<? super T, ?> myLabelProperty = AttachmentProperty.FILE_NAME;

  public AttachmentThumbnailUI(AttachmentsEnv env) {
    myEnv = env;
    myGeometry = new ThumbnailViewCellGeometry();
    myThumbnailer = Context.get(Thumbnailer.ROLE);
  }

  public void setLabelProperty(AttachmentProperty<? super T, ?> labelProperty) {
    myLabelProperty = labelProperty;
  }

  public ThumbnailViewCellGeometry getCellGeometry(java.util.List<T> list, JComponent c) {
    myRemakeGeometry = false;
    boolean fitImage = false;
    if (list.size() == 1 && myThumbnailer != null) {
      T item = list.get(0);
      File file = null;
      String thumbId = getThumbId(item);
      if (thumbId != null) {
        String url = item.getUrl();
        if (url != null) {
          DownloadedFile dfile = myEnv.getDownloadedFile(url);
          if (dfile != null && dfile.getState() == DownloadedFile.State.READY) {
            file = dfile.getFile();
          }
        } else {
          file = item.getFileForUpload();
        }
        if (file != null && file.isFile()) {
          Image image = myThumbnailer.getThumbnail(thumbId, BIG_THUMB, this, ThreadGate.LONG, this);
          if (image != null) {
            int height = image.getHeight(null);
            int width = image.getWidth(null);
            if (height > 0 && width > 0) {
              if (height <= MAX_THUMB_FIT_HEIGHT) {
                width = Math.max(width, 20) + 1;
                height = Math.max(height, 20) + 1;
              } else {
                width = Math.max((int) (1F * width * MAX_THUMB_FIT_HEIGHT / height), 20) + 1;
                height = MAX_THUMB_FIT_HEIGHT + 1;
              }
              setGeometry(width, height);
              fitImage = true;
            }
          } else {
            myRemakeGeometry = true;
          }
        }
      }
    }
    if (!fitImage) {
      FontMetrics metrics = c.getFontMetrics(c.getFont());
      int width = metrics.stringWidth("application/x-shellscript-x");
      int height = (int) ((metrics.getHeight() * 6 + 5) * 1.3F) +
        UIUtil.getProgressBarPreferredSize(myProgressBar).height;
      setGeometry(width, height);
    }
    return myGeometry;
  }

  private String getUploadThumbId(File file) {
    return UPLOAD_PREFIX + file.lastModified() + ID_SEPARATOR + file.getAbsolutePath();
  }

  private String getThumbId(String url, DownloadedFile dfile) {
    return url + ID_SEPARATOR + dfile.getLastModifed();
  }

  @Nullable
  private String getThumbId(T item) {
    String url = item.getUrl();
    if (url != null) {
      DownloadedFile dfile = myEnv.getDownloadedFile(url);
      if (dfile != null) {
        if (dfile.getState() == DownloadedFile.State.READY && ImageUtil.isImageMimeType(dfile.getMimeType())) {
          return getThumbId(url, dfile);
        }
      }
    }
    File file = item.getFileForUpload();
    if (file != null) {
      if (ImageUtil.isImageMimeType(FileUtil.guessMimeType(file.getName()))) {
        return getUploadThumbId(file);
      }
    }
    return null;
  }

  private void setGeometry(int width, int height) {
    myGeometry.setImageWidth(width);
    myGeometry.setImageHeight(height);
    myGeometry.setCellWidth(width + 20);
  }

  public void paintItemImage(JComponent c, Graphics g, T item, Rectangle r) {
    paintBorder(c, g, r);
    r.grow(-1, -1);
    boolean painted = paintThumbnailIfPossible(c, g, item, r);
    if (!painted) {
      r.grow(-1, 0);
      DownloadedFile dfile = myEnv.getDownloadedFile(item);
      paintBigHeader(c, g, item, dfile, r);
      paintMimeType(c, g, item, dfile, r);
      paintSize(c, g, item, dfile, r);
      advanceDown(r, 5);
      paintStatus(c, g, item, r);
    }
  }

  private boolean paintThumbnailIfPossible(JComponent c, Graphics g, T item, Rectangle r) {
    if (myThumbnailer == null)
      return false;
    String thumbId = getThumbId(item);
    if (thumbId == null)
      return false;

    Dimension thumbSize = new Dimension(myGeometry.getImageWidth(), myGeometry.getImageHeight());

    // get full-size image
    Image image = myThumbnailer.getThumbnail(thumbId, BIG_THUMB);
    if (image != null) {
      int h = image.getHeight(c);
      int w = image.getWidth(c);
      if (h <= 0 || h >= thumbSize.height || w <= 0 || w >= thumbSize.width) {
        image = null;
      }
    }

    if (image == null) {
      image = myThumbnailer.getThumbnail(thumbId, thumbSize, this, ThreadGate.LONG, this);
    }

    if (image == null) {
      // will repaint with notificator
      return false;
    }

    int h = image.getHeight(c);
    int w = image.getWidth(c);
    if (h <= 0 || w <= 0) {
      return false;
    }

    int x = r.x + Math.max(0, (r.width - w) / 2);
    int y = r.y + Math.max(0, (r.height - h) / 2);
    Graphics tempG = g.create();
    try {
      tempG.clipRect(r.x, r.y, r.width + 1, r.height + 1);
      if (!tempG.getClipBounds().isEmpty()) {
        tempG.drawImage(image, x, y, c);
      }
    } finally {
      tempG.dispose();
    }
    return true;
  }

  @ThreadAWT
  public void onThumbnailReady(String imageId, Image thumbnail) {
    if (myRemakeGeometry) {
      myEnv.revalidateAttachments();
    } else {
      myEnv.repaintAttachment(StringUtil.substringBeforeFirst(imageId, ID_SEPARATOR));
    }
  }

  @CanBlock
  @Nullable
  public Image createSourceImage(String imageId) {
    Threads.assertLongOperationsAllowed();
    String url = StringUtil.substringBeforeFirst(imageId, ID_SEPARATOR);
    String mimeType = null;
    File file = null;
    if (url.length() > 0) {
      DownloadedFile dfile = myEnv.getDownloadedFile(url);
      if (dfile != null) {
        mimeType = getMimeType(dfile);
        file = dfile.getFile();
      }
    } else if (imageId.startsWith(UPLOAD_PREFIX)) {
      String filename = StringUtil.substringAfterLast(imageId, ID_SEPARATOR);
      if (!filename.equals(imageId)) {
        file = new File(filename);
        mimeType = FileUtil.guessMimeType(filename);
      }
    }
    if (file != null) {
      return ImageUtil.loadImageFromFile(file, mimeType);
    } else {
      return null;
    }
  }


  @ThreadSafe
  private String getMimeType(@NotNull DownloadedFile dfile) {
    File file = dfile.getFile();
    if (file == null)
      return DEFAULT_MIME_TYPE;
    String mimeType = dfile.getMimeType();
    if (mimeType == null) {
      mimeType = FileUtil.guessMimeType(file.getName());
    }
    return mimeType == null ? DEFAULT_MIME_TYPE : mimeType;
  }

  private void paintStatus(JComponent c, Graphics g, T item, Rectangle r) {
    if (r.height < 0)
      return;

    DownloadedFile dfile = myEnv.getDownloadedFile(item);
    DownloadedFile.State state = dfile == null ? DownloadedFile.State.UNKNOWN : dfile.getState();
    String stateString;
    Color stateColor;
    if (item.isLocal()) {
      stateString = "Waiting for Upload";
      stateColor = c.getForeground();
    } else {
      stateString = AttachmentsPanel.getStateString(item, state);
      stateColor = DownloadedFile.State.getStateColor(c, state);
    }
    paintTextLine(g, c, stateString, r, stateColor, SwingConstants.CENTER);

    if (state == DownloadedFile.State.DOWNLOAD_ERROR) {
      assert dfile != null;
      String error = dfile.getLastDownloadError();
      if (error != null && error.length() > 0) {
        paintTextLine(g, c, error, r, stateColor, SwingConstants.CENTER);
      }
    } else if (state == DownloadedFile.State.DOWNLOADING) {
      assert dfile != null;
      ProgressData progress = dfile.getDownloadProgressSource().getProgressData();
      double numeric = progress.getProgress();
      Object activity = progress.getActivity();
      if (myRendererPane.getParent() != c) {
        c.add(myRendererPane);
      }
      myProgressBar.setMinimum(0);
      myProgressBar.setMaximum(100);
      myProgressBar.setValue((int) (100F * numeric));
      int prefHeight = UIUtil.getProgressBarPreferredSize(myProgressBar).height;

      int height = Math.min(prefHeight, r.height);
      r.grow(-2, 0);
      myRendererPane.paintComponent(g, myProgressBar, c, r.x, r.y, r.width, height);
      advanceDown(r, height + 2);

      if (activity != null && r.height > 0) {
        paintTextLine(g, c, String.valueOf(activity), r, c.getForeground(), SwingConstants.LEADING);
      }
      r.grow(2, 0);
    }
  }

  private void paintMimeType(JComponent c, Graphics g, T item, DownloadedFile dfile, Rectangle r) {
    String mimeType = AttachmentProperty.MIME_TYPE.getColumnValue(item, dfile);
    if (mimeType == null)
      return;
    paintTextLine(g, c, mimeType, r, c.getForeground(), SwingConstants.CENTER);
  }

  private void paintSize(JComponent c, Graphics g, T item, DownloadedFile dfile, Rectangle r) {
    String sizeText = AttachmentProperty.SIZE.getStringValue(item, dfile);
    if (sizeText == null)
      return;
    paintTextLine(g, c, sizeText, r, c.getForeground(), SwingConstants.CENTER);
  }

  private void paintTextLine(Graphics g, JComponent c, String text, Rectangle r, Color color, int alignment) {
    if (r.height <= 0)
      return;
    Font font = c.getFont();
    g.setFont(font);
    g.setColor(color);
    FontMetrics fm = c.getFontMetrics(font);
    Rectangle textR = new Rectangle();
    text = SwingUtilities.layoutCompoundLabel(fm, text, null, SwingConstants.TOP, alignment, SwingConstants.CENTER,
      SwingConstants.LEADING, r, new Rectangle(), textR, 0);
    g.drawString(text, textR.x, textR.y + fm.getAscent());
    advanceDown(r, textR.y + textR.height - r.y);
  }

  private void paintBigHeader(JComponent c, Graphics g, T item, DownloadedFile dfile, Rectangle r) {
    g.setColor(c.getForeground());

    String fileName = AttachmentProperty.FILE_NAME.getColumnValue(item, dfile);
    String fileHeader = null;
    if (fileName != null) {
      int k = fileName.lastIndexOf('.');
      if (k >= 0) {
        fileHeader = fileName.substring(k + 1);
      }
    }
    if (fileHeader == null) {
      return;
    }

    fileHeader = Util.upper(fileHeader);

    Font baseFont = c.getFont();
    Font font = baseFont.deriveFont((float) (baseFont.getSize2D() * 1.5F));
    g.setFont(font);
    FontMetrics fm = c.getFontMetrics(font);

    Rectangle textR = new Rectangle();
    fileHeader = SwingUtilities.layoutCompoundLabel(fm, fileHeader, null, SwingConstants.TOP, SwingConstants.CENTER,
      SwingConstants.CENTER, SwingConstants.LEADING, r, new Rectangle(), textR, 0);

    int spaceBelow = r.y + r.height - textR.y - textR.height;
    if (spaceBelow > 30) {
      textR.y += (int) ((float) spaceBelow) / 6.5F;
    }

    g.drawString(fileHeader, textR.x, textR.y + fm.getAscent());

    advanceDown(r, textR.y + textR.height - r.y + 3);
  }

  private void advanceDown(Rectangle r, int decrease) {
    r.y += decrease;
    r.height -= decrease;
  }

  private void paintBorder(JComponent c, Graphics g, Rectangle r) {
    Color borderColor = ColorUtil.between(c.getBackground(), c.getForeground(), 0.5F);
    g.setColor(borderColor);
    g.drawRect(r.x, r.y, r.width, r.height);
  }

  public String getItemText(T item) {
    String label = null;
    AttachmentProperty<? super T, ?> labelProperty = myLabelProperty;
    if (labelProperty != null) {
      label = labelProperty.getStringValue(item, myEnv.getDownloadedFile(item));
    }
    if (label == null) {
      label = item.getFilename();
    }
    if (label == null) {
      label = "?";
    }
    return label;
  }

  public String getTooltipText(T item) {
    return myEnv.getTooltipText(item);
  }

  public boolean isTransferSupported() {
    return true;
  }

  @Nullable
  public Transferable createTransferable(List<T> items) {
    if (items == null || items.size() == 0)
      return null;
    List<File> files = Collections15.arrayList();
    String mimeType = null;
    boolean sameMimeType = true;
    for (T item : items) {
      File file = null;
      String mime = null;
      if (item.isLocal()) {
        file = item.getFileForUpload();
        if (file != null) {
          mime = FileUtil.guessMimeType(file.getName());
        }
      } else {
        DownloadedFile dfile = myEnv.getDownloadedFile(item);
        if (dfile != null && dfile.getState() == DownloadedFile.State.READY) {
          file = dfile.getFile();
          mime = getMimeType(dfile);
        }
      }
      if (file == null || !file.isFile())
        continue;
      files.add(file);
      if (sameMimeType) {
        if (mimeType == null) {
          mimeType = mime;
        } else if (!mimeType.equalsIgnoreCase(mime)) {
          mimeType = null;
          sameMimeType = false;
        }
      }
    }
    if (files.size() == 0)
      return null;
    else
      return new FileTransferable(files, sameMimeType ? mimeType : null);
  }


  private static class FileTransferable implements Transferable {
    private final DataFlavor[] myFlavors;
    private final List<File> myFiles;
    private final String myMimeType;

    public FileTransferable(List<File> files, String mimeType) {
      myFiles = files;
      myMimeType = mimeType;
      boolean image = files.size() == 1 && ImageUtil.isImageMimeType(mimeType);
      myFlavors = new DataFlavor[image ? 4 : 3];
      int i = 0;
      if (image) {
        myFlavors[i++] = DataFlavor.imageFlavor;
      }
      myFlavors[i++] = DataFlavor.javaFileListFlavor;
      myFlavors[i++] = DndHack.uriListFlavor;
      myFlavors[i++] = DataFlavor.stringFlavor;
    }


    public DataFlavor[] getTransferDataFlavors() {
      return myFlavors;
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
      for (DataFlavor f : myFlavors) {
        if (f.equals(flavor))
          return true;
      }
      return false;
    }

    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
      if (DataFlavor.imageFlavor.equals(flavor)) {
        if (myFiles.size() != 1 || !ImageUtil.isImageMimeType(myMimeType))
          throw new UnsupportedFlavorException(flavor);
        File file = myFiles.get(0);
        Image image = ImageUtil.loadImageFromFile(file, myMimeType);
        if (image == null)
          throw new IOException("cannot load image");
        return image;
      } else if (DataFlavor.javaFileListFlavor.equals(flavor)) {
        return myFiles;
      } else if (DndHack.uriListFlavor.equals(flavor)) {
        return DndHack.getUriListObject(myFiles);
      } else if (DataFlavor.stringFlavor.equals(flavor)) {
        if (myFiles.size() == 1) {
          return myFiles.get(0).getPath();
        } else {
          StringBuffer sb = new StringBuffer();
          for (File file : myFiles) {
            sb.append(file.getPath());
            sb.append("\n");
          }
          return sb.toString();
        }
      } else {
        throw new UnsupportedFlavorException(flavor);
      }
    }
  }
}
