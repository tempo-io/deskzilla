package com.almworks.api.actions;

import com.almworks.api.application.*;
import com.almworks.api.edit.ItemSyncSupport;
import com.almworks.api.explorer.ItemModelRegistry;
import com.almworks.api.gui.BasicWindowBuilder;
import com.almworks.api.gui.WindowManager;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.EditControl;
import com.almworks.util.commons.Function;
import com.almworks.util.commons.FunctionE;
import com.almworks.util.ui.actions.*;
import org.jetbrains.annotations.*;

import javax.swing.*;

/**
 * Base class for action that starts edit of a single item, showing editor window.
 * @deprecated todo check what is derived from BaseEditItemAction, move it to a new abstract EditorWindowCreator.
 * take care of the interface because
 *  1) for non-locking edits, createWindow consumes items from context (not loaded). They may become ItemUiModelImpls by calling IUMI.create(ItemWrapper).
 *  2) for locking edits, single item is loaded in DB read transaction
 */
@Deprecated
public abstract class BaseEditItemAction extends SimpleAction {
  private final DataRole<? extends UiItem> myLockRole;
  /**
   * @param lockRole Specify non-null value to denote the type of items that should be locked before edit or null if no items should be locked in advance
   */
  public BaseEditItemAction(@Nullable String text, Icon icon, @Nullable DataRole<? extends UiItem> lockRole) {
    super(text, icon);
    myLockRole = lockRole;
    watchRole(ItemWrapper.ITEM_WRAPPER);
    if (lockRole != null && lockRole != ItemWrapper.ITEM_WRAPPER) {
      watchRole(lockRole);
    }
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    ItemActionUtils.updateForEditSingleItem(context, myLockRole);
  }

  protected final void doPerform(final ActionContext context) throws CantPerformException {
    final EditControl control = ItemSyncSupport.prepareEditOrShowLockingEditor(context, context.getSourceCollection(myLockRole));

    final LoadedItem loadedItem = context.getSourceObject(LoadedItem.LOADED_ITEM);
    final ItemModelRegistry modelReg = context.getSourceObject(ItemModelRegistry.ROLE);
    final WindowManager windowManager = context.getSourceObject(WindowManager.ROLE);

    if (control != null) {
      ItemSyncSupport.startWindowedEdit(control, new Function<DBReader, ItemUiModelImpl>() {
        @Override
        public ItemUiModelImpl invoke(DBReader reader) {
          return modelReg.createNewModel(loadedItem.getItem(), reader);
        }
      }, new FunctionE<ItemUiModelImpl, BasicWindowBuilder, CantPerformException>() {
        @Override
        public BasicWindowBuilder invoke(ItemUiModelImpl model) throws CantPerformException {
          return model == null ? null : setupEditor(windowManager, model, context, control);
        }
      });
    } else {
      ItemUiModelImpl model = ItemUiModelImpl.create(loadedItem);
      setupEditor(windowManager, model, context, null).showWindow();
    }
  }

  @NotNull
  private BasicWindowBuilder setupEditor(WindowManager windowManager, ItemUiModelImpl model, ActionContext context, EditControl control) throws CantPerformException {
    BasicWindowBuilder builder = CantPerformException.ensureNotNull(
      setupWindowBuilder(windowManager, model, context)
    );
    return builder;
  }

  @Nullable
  protected abstract BasicWindowBuilder setupWindowBuilder(WindowManager manager, @NotNull ItemUiModelImpl item, ActionContext context) throws CantPerformException;
}
