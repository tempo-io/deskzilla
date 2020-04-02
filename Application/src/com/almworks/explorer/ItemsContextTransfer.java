package com.almworks.explorer;

import com.almworks.api.actions.*;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.LoadedItem;
import com.almworks.util.ContinueOrBreak;
import com.almworks.util.files.FileUtil;
import com.almworks.util.images.ImageUtil;
import com.almworks.util.ui.actions.CantPerformException;
import com.almworks.util.ui.actions.OverridingSourceActionContext;
import com.almworks.util.ui.actions.dnd.*;
import org.almworks.util.Collections15;
import org.almworks.util.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author dyoma
 */
public class ItemsContextTransfer extends BaseItemContextTransfer {
  public void acceptTransfer(DragContext context, Transferable transferred)
    throws CantPerformException, UnsupportedFlavorException, IOException
  {
    final ItemWrapper target = getTarget(context);
    checkTarget(target);

    try {
      tryAcceptArtifacts(context, target);
      tryAcceptFiles(context, target, transferred);
      tryAcceptImage(context, target, transferred);
      // more?
    } catch(ContinueOrBreak cob) {
      return;
    }
  }

  private void checkTarget(ItemWrapper target) throws CantPerformException {
    if(target == null) {
      throw new CantPerformException("no target");
    }

    if(!(target instanceof LoadedItem)) {
      throw new CantPerformException("target class " + target.getClass());
    }
  }

  private void tryAcceptArtifacts(DragContext context, ItemWrapper target)
    throws ContinueOrBreak, CantPerformException
  {
    final List<ItemWrapper> items = context.getTransferData(ItemWrappersTransferrable.ARTIFACTS_FLAVOR);
    if(items != null && items.size() > 0) {
      importArtifacts(context, target, items);
      ContinueOrBreak.throwBreak();
    }
  }

  private void tryAcceptImage(DragContext context, ItemWrapper target, Transferable transferred)
    throws ContinueOrBreak, CantPerformException
  {
    final Image image = getImage(transferred);
    if(image != null) {
      acceptImage(context, target, image);
      ContinueOrBreak.throwBreak();
    }
  }

  private void tryAcceptFiles(DragContext context, ItemWrapper target, Transferable transferred)
    throws ContinueOrBreak, CantPerformException
  {
    final List<File> files = getFiles(transferred);
    if(files != null && files.size() > 0) {
      tryAcceptSingleImageFile(context, target, files);
      tryAcceptRegularFiles(context, target, files);
    }
  }

  private void tryAcceptSingleImageFile(DragContext context, ItemWrapper target, List<File> files)
    throws CantPerformException, ContinueOrBreak
  {
    if(files.size() == 1) {
      final Image image = getImageFromFile(files.get(0));
      if(image != null) {
        acceptImage(context, target, image);
        ContinueOrBreak.throwBreak();
      }
    }
  }

  private void tryAcceptRegularFiles(DragContext context, ItemWrapper target, List<File> files)
    throws CantPerformException, ContinueOrBreak
  {
    acceptFiles(context, target, files);
    ContinueOrBreak.throwBreak();
  }
  
  private Image getImageFromFile(File file) {
    String mime = FileUtil.guessMimeType(file.getName());
    if (!ImageUtil.isImageMimeType(mime) && !ImageUtil.isBitmap(file, mime)) {
      return null;
    }

    // kludge: loading in AWT thread
    Image image = ImageUtil.loadImageFromFile(file, mime);
    if (image == null) {
      Log.warn("file " + file + " cannot be read as image");
      return null;
    }

    // ensure image is loaded
    ImageIcon icon = new ImageIcon(image);
    icon.getImage();

    int loadStatus = icon.getImageLoadStatus();
    if (loadStatus == MediaTracker.ABORTED || loadStatus == MediaTracker.ERRORED) {
      Log.warn("cannot load " + file + " as image");
      return null;
    }

    return image;
  }

  private static void acceptFiles(DragContext context, ItemWrapper target, List<File> files)
    throws CantPerformException
  {
    OverridingSourceActionContext ac = new OverridingSourceActionContext(context);
    ac.override(BaseAttachFileAction.ATTACHMENTS, files);
    ac.override(LoadedItem.LOADED_ITEM, (LoadedItem) target);
    ac.override(LoadedItem.ITEM_WRAPPER, target);
    StdItemActions.ATTACH_FILE.perform(ac);
  }

  private static void acceptImage(DragContext context, ItemWrapper target, Image image)
    throws CantPerformException
  {
    OverridingSourceActionContext ac = new OverridingSourceActionContext(context);
    ac.override(AttachScreenshotAction.IMAGE, image);
    ac.override(LoadedItem.LOADED_ITEM, (LoadedItem) target);
    ac.override(LoadedItem.ITEM_WRAPPER, target);
    StdItemActions.ATTACH_SCREENSHOT.perform(ac);
  }

  private Image getImage(Transferable transferred) {
    if (DndUtil.isDataFlavorSupported(transferred, DataFlavor.imageFlavor)) {
      Object data = null;
      try {
        data = transferred.getTransferData(DataFlavor.imageFlavor);
      } catch (Exception e) {
        // skip
      }
      if (data instanceof Image)
        return (Image) data;
    }
    return null;
  }

  private List<File> getFiles(Transferable transferred) throws CantPerformException {
    List<File> r = null;
    Object data;
    if (DndUtil.isDataFlavorSupported(transferred, DataFlavor.javaFileListFlavor)) {
      try {
        data = transferred.getTransferData(DataFlavor.javaFileListFlavor);
      } catch (Exception e) {
        throw new CantPerformException(e);
      }
      if (data instanceof List) {
        List list = (List) data;
        r = Collections15.arrayList();
        for (Object o : list) {
          if (o instanceof File) {
            File file = (File) o;
            if (file.isFile() && file.canRead()) {
              r.add(file);
            }
          }
        }
      }
    } else if (DndUtil.isDataFlavorSupported(transferred, DndHack.uriListFlavor)) {
      try {
        data = transferred.getTransferData(DndHack.uriListFlavor);
      } catch (Exception e) {
        throw new CantPerformException(e);
      }
      if (data instanceof String) {
        String uriList = (String) data;
        String[] list = uriList.split("[\\r\\n]+");
        r = Collections15.arrayList();
        for (String uri : list) {
          try {
            File file = new File(new URI(uri));
            if (file.isFile() && file.canRead()) {
              r.add(file);
            }
          } catch (URISyntaxException e) {
            Log.debug("cannot understand " + uri, e);
          }
        }
      }
    }
    return r != null && r.size() > 0 ? r : null;
  }

  private void importArtifacts(DragContext context, ItemWrapper target, List<ItemWrapper> items)
    throws CantPerformException
  {
    target.getMetaInfo().importItems(target, items, context);
  }

  private List<File> extractFileList(String data) {
    String delim = System.getProperty("line.separator");
    String[] strings = data.split(delim);

    ArrayList<File> files = new ArrayList<File>(1);
    for (String string : strings) {
      if (string.startsWith("file://")) {
        string = string.substring(7);
      }
      files.add(new File(string));
    }

    return files;
  }

  public boolean canImportFlavor(DataFlavor flavor) {
    boolean r = super.canImportFlavor(flavor) || isAttachableFlavor(flavor);
    return r;
  }

  private boolean isAttachableFlavor(DataFlavor flavor) {
    return DataFlavor.javaFileListFlavor.equals(flavor) || DndHack.uriListFlavor.equals(flavor) ||
      DataFlavor.imageFlavor.equals(flavor);
  }

  protected boolean canImportDataNowToTarget(DragContext context, ItemWrapper target) throws CantPerformException {
    if (super.canImportDataNowToTarget(context, target))
      return true;
    return hasFileListTransferable(context);
  }

  private static boolean hasFileListTransferable(DragContext context) {
    Transferable transferable = context.getTransferable();
    return DndUtil.isDataFlavorSupported(transferable, DndHack.uriListFlavor) ||
      DndUtil.isDataFlavorSupported(transferable, DataFlavor.javaFileListFlavor) ||
      DndUtil.isDataFlavorSupported(transferable, DataFlavor.imageFlavor);
  }

  public boolean canImportData(DragContext context) throws CantPerformException {
    return super.canImportData(context) || hasFileListTransferable(context);
  }
}
