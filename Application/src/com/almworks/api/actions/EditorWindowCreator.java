package com.almworks.api.actions;

import com.almworks.api.application.ItemWrapper;
import com.almworks.api.explorer.ItemModelRegistry;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.EditPrepare;
import com.almworks.items.sync.ItemEditor;
import com.almworks.util.threads.ThreadAWT;
import com.almworks.util.ui.actions.ActionContext;
import com.almworks.util.ui.actions.CantPerformException;
import org.jetbrains.annotations.*;

import java.util.List;

public interface EditorWindowCreator {
  @NotNull @ThreadAWT
  LongList getItemsToLock(List<ItemWrapper> primaryItems, ActionContext context) throws CantPerformException;

  /**
   * Implementation must reload all UI data needed for editing in the read transaction
   * @param lockedItems
   * @param context context of the action in which edit was started
   */
  @Nullable
  ItemEditor createItemEditor(@NotNull LongList lockedItems, @NotNull List<ItemWrapper> primaryItems,
    @NotNull DBReader reader, @NotNull EditPrepare prepare, @NotNull ItemModelRegistry registry,
    @NotNull ActionContext context);
}
