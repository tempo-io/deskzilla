package com.almworks.api.actions;

import com.almworks.api.application.DBDataRoles;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.edit.ItemSyncSupport;
import com.almworks.api.engine.Connection;
import com.almworks.api.explorer.ItemModelRegistry;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import com.almworks.util.commons.Condition;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.List;

/**
 * Base action for all actions that require locks on items. The action is concerned with the beginning of the editing process -- locking necessary items, setting up a window if needed -- and not its ending.
 * Commit may be performed by the editor window button or immediately after the start if action does not need any user-visible editors.
 */
public abstract class BaseEditAction extends SimpleAction {
  private final Condition<Integer> myItemsNumber;

  protected static final Condition<Integer> SINGLE_ITEM = Condition.isEqual(1);
  protected static final Condition<Integer> NON_ZERO = Condition.not(Condition.isEqual(0));

  public BaseEditAction(@NotNull Condition<Integer> itemsNumber) {
    myItemsNumber = itemsNumber;
    watchRoles();
  }

  public BaseEditAction(@Nullable String text, Icon icon, @NotNull Condition<Integer> itemsNumber) {
    super(text, icon);
    myItemsNumber = itemsNumber;
    watchRoles();
  }

  private void watchRoles() {
    watchRole(ItemWrapper.ITEM_WRAPPER);
    watchModifiableRole(SyncManager.MODIFIABLE);
  }

  public abstract EditorWindowCreator getWindowCreator();

  @Override
  protected void customUpdate(UpdateContext context) throws CantPerformException {
    DBDataRoles.checkAnyConnectionHasCapability(context, Connection.Capability.EDIT_ITEM);
    List<ItemWrapper> primaryItems = ItemActionUtils.basicUpdate(context);
    updateUnconditional(context, primaryItems);

    int nItems = primaryItems.size();
    CantPerformException.ensure(myItemsNumber.isAccepted(nItems));

    EditorWindowCreator creator = getWindowCreator();
    if (creator != null) {
      LongList itemsToEdit = creator.getItemsToLock(primaryItems, context);
      ItemActionUtils.checkNotLocked(context, itemsToEdit);
    }

    if (nItems == 0) {
      context.setEnabled(EnableState.INVISIBLE);
    } else if (checkAllConnectionsCanEdit(primaryItems)) {
      context.setEnabled(EnableState.ENABLED);
      updateEnabledAction(context, primaryItems);
    } else {
      context.setEnabled(EnableState.DISABLED);
    }
  }

  private static boolean checkAllConnectionsCanEdit(List<ItemWrapper> items) {
    for (ItemWrapper item : items) {
      Connection connection = item.getConnection();
      if (connection == null || !connection.hasCapability(Connection.Capability.EDIT_ITEM)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Update that should take place even if context is disabled -- sets up presentation properties, adds subscriptions.
   */
  protected void updateUnconditional(UpdateContext context, List<ItemWrapper> items) throws CantPerformException {
  }

  /**
   * Called only if action can be enabled (all items are not locked, connections are ok and can edit). Implementation may disable action if necessary.
   */
  protected void updateEnabledAction(UpdateContext context, List<ItemWrapper> items) throws CantPerformException {
  }

  @Override
  protected void doPerform(final ActionContext context) throws CantPerformException {
    final List<ItemWrapper> primaryItems = context.getSourceCollection(ItemWrapper.ITEM_WRAPPER);
    CantPerformException.ensure(myItemsNumber.isAccepted(primaryItems.size()));
    SyncManager syncMan = context.getSourceObject(SyncManager.ROLE);
    final EditorWindowCreator windowCreator = getWindowCreator();
    final LongList itemIds = windowCreator.getItemsToLock(primaryItems, context);
    assert !itemIds.isEmpty();
    final EditControl editControl = ItemSyncSupport.prepareEditOrShowLockingEditor(syncMan, itemIds);

    if (isWindowNeeded(primaryItems)) {
      final ItemModelRegistry modelReg = context.getSourceObject(ItemModelRegistry.ROLE);
      editControl.start(new EditorFactory() {
        @Override
        public ItemEditor prepareEdit(DBReader reader, final EditPrepare prepare) {
          return windowCreator.createItemEditor(itemIds, primaryItems, reader, prepare, modelReg, context);
        }
      });
    } else {
      syncMan.commitEdit(itemIds, createCommit(itemIds, primaryItems, context));
    }
  }

  protected abstract boolean isWindowNeeded(List<ItemWrapper> items) throws CantPerformException;

  /**
   * Called if !{@link #isWindowNeeded(java.util.List}
   */
  protected abstract EditCommit createCommit(LongList editedItems, List<ItemWrapper> primaryItems, ActionContext context) throws CantPerformException;

  public static abstract class EditInWindowAction extends BaseEditAction {
    public EditInWindowAction(@NotNull Condition<Integer> itemsNumber) {
      super(itemsNumber);
    }

    public EditInWindowAction(@Nullable String text, Icon icon, @NotNull Condition<Integer> itemsNumber) {
      super(text, icon, itemsNumber);
    }

    @Override
    protected boolean isWindowNeeded(List<ItemWrapper> items) throws CantPerformException {
      return true;
    }

    @Override
    protected EditCommit createCommit(LongList editedItems, List<ItemWrapper> primaryItems, ActionContext context) throws CantPerformException {
      assert false;
      Log.warn(getClass().getSimpleName() + ": should not be there");
      return null;
    }
  }
}
