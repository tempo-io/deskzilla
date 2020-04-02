package com.almworks.api.actions;

import com.almworks.api.application.DBDataRoles;
import com.almworks.api.application.LoadedItem;
import com.almworks.api.engine.Connection;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.screenshot.ScreenShooter;
import com.almworks.api.screenshot.Screenshot;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.sync.*;
import com.almworks.util.Terms;
import com.almworks.util.commons.Procedure;
import com.almworks.util.commons.Procedure2;
import com.almworks.util.images.Icons;
import com.almworks.util.properties.Role;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Log;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;


public abstract class AttachScreenshotAction extends SimpleAction {
  public static final Role<Image> IMAGE = Role.role("image");

  protected AttachScreenshotAction() {
    super("Attach Screenshot\u2026", Icons.ACTION_ATTACH_SCREENSHOT);
    setDefaultPresentation(
      PresentationKey.SHORT_DESCRIPTION,
      "Attach a screenshot to the selected " + Terms.ref_artifact);
  }
  

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    DBDataRoles.checkAnyConnectionHasCapability(context, Connection.Capability.EDIT_ITEM);
    ItemActionUtils.basicUpdate(context);
    LoadedItem item = context.getSourceObject(LoadedItem.LOADED_ITEM);
    CantPerformException.ensure(CantPerformException.ensureNotNull(item.getConnection()).hasCapability(Connection.Capability.EDIT_ITEM));
  }

  protected void doPerform(final ActionContext context) throws CantPerformException {
    final long primaryItem = context.getSourceObject(LoadedItem.LOADED_ITEM).getItem();
    final SyncManager syncMan = context.getSourceObject(SyncManager.ROLE);
    attach(context, new Procedure2<File, String>() {
      public void invoke(final File file, final String description) {
        syncMan.commitEdit(new EditCommit.Adapter() {
          @Override
          public void performCommit(EditDrain drain) throws DBOperationCancelledException {
            makeAttach(primaryItem, file, description, drain);
          }
        });
      }
    });
  }

  public static void attach(ActionContext context, final Procedure2<File, String> callback) throws CantPerformException {
    ScreenShooter ss = context.getSourceObject(ScreenShooter.ROLE);
    Image image = null;
    try {
      image = context.getSourceObject(IMAGE);
    } catch (CantPerformException e) {
      // fall through
    }
    final File uploadDir = context.getSourceObject(WorkArea.APPLICATION_WORK_AREA).getUploadDir();
    Procedure<Screenshot> acceptor = new Procedure<Screenshot>() {
      public void invoke(Screenshot arg) {
        File output = null;
        String prefix = uploadDir + File.separator + "screenshot";
        int i = 0;
        do {
          if (i > 10000) {
            Log.warn("cannot create screenshot file");
            return;
          }
          output = new File(prefix + (i++) + ".png");
        } while (output.exists());

        try {
          ImageIO.write(arg.getImage(), "png", output);
        } catch (IOException e) {
          Log.warn("cannot save  screenshot", e);
        }
        callback.invoke(output, arg.getDescription());
      }
    };
    if (image == null)
      ss.shoot(context.getComponent(), acceptor);
    else
      ss.edit(image, acceptor);
  }

  protected abstract void makeAttach(long primaryItem, File file, String description, EditDrain drain);
}