package com.almworks.bugzilla.gui.flags.edit;

import com.almworks.api.actions.ItemActionUtils;
import com.almworks.api.application.DBDataRoles;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.edit.ItemSyncSupport;
import com.almworks.api.edit.WindowItemEditor;
import com.almworks.api.engine.Connection;
import com.almworks.api.explorer.ItemModelRegistry;
import com.almworks.api.gui.*;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.datalink.flags2.*;
import com.almworks.edit.EditLifecycleImpl;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.integers.LongArray;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;

import java.util.List;

import static org.almworks.util.Collections15.NNList;

public class EditFlagsAction extends SimpleAction {
  public static final AnAction INSTANCE = new EditFlagsAction();
  
  public EditFlagsAction() {
    super("Edit Flags", Icons.FLAG);
    watchRole(ItemWrapper.ITEM_WRAPPER);
  }

  @Override
  protected void customUpdate(UpdateContext context) throws CantPerformException {
    DBDataRoles.checkAnyConnectionHasCapability(context, Connection.Capability.EDIT_ITEM);
    ItemWrapper item = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    DBDataRoles.checkExisting(item);
    DBDataRoles.setEnabledIfConnectionCapableOf(context, item.getConnection(), Connection.Capability.EDIT_ITEM);
    BugzillaConnection connection = CantPerformException.ensureNotNull(BugzillaConnection.getInstance(item));
    if (!connection.hasFlags()) {
      context.setEnabled(EnableState.INVISIBLE);
      throw new CantPerformException();
    }
    context.watchModifiableRole(SyncManager.MODIFIABLE);
    List<FlagVersion> flags = NNList(item.getModelKeyValue(FlagsModelKey.MODEL_KEY));
    ItemActionUtils.checkNotLocked(context, flags);
  }

  @Override
  protected void doPerform(final ActionContext context) throws CantPerformException {
    ItemWrapper item = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    DBDataRoles.checkExisting(item);
    List<FlagVersion> flags = NNList(item.getModelKeyValue(FlagsModelKey.MODEL_KEY));
    ItemActionUtils.checkNotLocked(context, flags);
    EditControl control = ItemSyncSupport.prepareEditOrShowLockingEditor(context, flags);
    ItemModelRegistry modelReg = context.getSourceObject(ItemModelRegistry.ROLE);
    WindowManager windowManager = context.getSourceObject(WindowManager.ROLE);
    long bugItem = item.getItem();
    control.start(new MyEditorFactory(modelReg, bugItem, windowManager));
  }

  public static String getTitle(ItemUiModelImpl model) throws DBOperationCancelledException {
    Connection connection = model.getConnection();
    if (connection == null) throw new DBOperationCancelledException();
    String sid = connection.getDisplayableItemId(model);
    String termBug = Local.parse(Terms.ref_Artifact);
    return L.dialog(sid != null
      ? "Flags for " + termBug + " " + sid
      : "Flags for New " + termBug);
  }

  private class MyEditorFactory implements EditorFactory {
    private final ItemModelRegistry myModelReg;
    private final long myBugItem;
    private final WindowManager myWindowManager;

    public MyEditorFactory(ItemModelRegistry modelReg, long bugItem, WindowManager windowManager) {
      myModelReg = modelReg;
      myBugItem = bugItem;
      myWindowManager = windowManager;
    }

    @Override
    public ItemEditor prepareEdit(DBReader reader, EditPrepare prepare) {
      if (!addAppearedFlagsToLock(prepare, reader)) {
        return null;
      }
      final ItemUiModelImpl model = myModelReg.createNewModel(myBugItem, reader);
      return model == null ? null : new WindowItemEditor(prepare, WindowItemEditor.frame(myWindowManager, "editFlags")) {
        @Override
        public void setupWindow(BasicWindowBuilder builder, EditLifecycleImpl editLife) throws CantPerformException {
          builder.setTitle(getTitle(model));
          FlagEditor editor = FlagEditor.create(model, true);
          editLife.setupEditModelWindow(builder, model, new IdActionProxy(MainMenu.ItemEditor.SAVE_DRAFT));
          builder.setContent(editor);
        }
      };
    }

    /**
     *  We've locked flags using UI knowledge which flags belong to the bug;
     *  some more could be added since that knowledge had been obtained.
     *  We'll need to query flags once again and try to add them to lock.
     */
    private boolean addAppearedFlagsToLock(EditPrepare prepare, DBReader reader) {
      ItemVersion bugReader = SyncUtils.readTrunk(reader, myBugItem);
      LongArray addedFlags = bugReader.getSlaves(Flags.AT_FLAG_MASTER);
      addedFlags.removeAll(prepare.getItems());
      return prepare.addItems(addedFlags);
    }
  }
}
