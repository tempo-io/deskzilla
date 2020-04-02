package com.almworks.api.actions;

import com.almworks.api.application.*;
import com.almworks.api.dynaforms.EditPrimitive;
import com.almworks.api.edit.WindowItemEditor;
import com.almworks.api.explorer.ItemModelRegistry;
import com.almworks.api.gui.DefaultCloseConfirmation;
import com.almworks.api.gui.FrameBuilder;
import com.almworks.edit.EditLifecycleImpl;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.explorer.workflow.EditorContent;
import com.almworks.integers.LongList;
import com.almworks.integers.LongListIterator;
import com.almworks.integers.util.LongListConcatenation;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.EditPrepare;
import com.almworks.items.sync.ItemEditor;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.collections.LongSet;
import com.almworks.util.config.Configuration;
import com.almworks.util.i18n.Local;
import com.almworks.util.ui.actions.ActionContext;
import com.almworks.util.ui.actions.CantPerformException;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.List;

/**
 * Constructs editor window from edit primitives.
 */
public abstract class ModularEditorWindow implements EditorWindowCreator {
  @NotNull
  @Override
  public final LongList getItemsToLock(List<ItemWrapper> primaryWrappers, ActionContext context) throws CantPerformException {
    LongList primary = LongSet.collect(UiItem.GET_ITEM, primaryWrappers);
    return new LongListConcatenation(primary, addToLock(primaryWrappers, context));
  }

  /**
   * Override this method to add some more items to the lock.
   */
  // todo :refactoring: could add such method to EditPrimitive, but getActionFields (the way to obtain edit primitives) seems to be too heavy for method that is called from action update. Maybe it isn't heavy, check this when doing concrete actions.
  @NotNull
  protected LongList addToLock(List<ItemWrapper> primaryItems, ActionContext context) throws CantPerformException {
    return LongList.EMPTY;
  }

  // todo :refactoring: could add such method to EditPrimitive, but getActionFields (the way to obtain edit primitives) seems to be too heavy for method that is called from action update. Maybe it isn't heavy, check this when doing concrete actions.
  protected LongList addToLock(List<ItemWrapper> primaryItems, DBReader reader) {
    return LongList.EMPTY;
  }

  @Override
  public ItemEditor createItemEditor(@NotNull LongList lockedItems, @NotNull List<ItemWrapper> primaryItems, @NotNull DBReader reader, @NotNull EditPrepare prepare, @NotNull ItemModelRegistry registry,
    @NotNull final ActionContext context)
  {
    // we've locked items judging by latest UI status; some more could be added since then, we'll need to try to add them to the lock
    if (!prepare.addItems(addToLock(primaryItems, reader))) {
      return null;
    }

    final List<ItemUiModelImpl> primary = Collections15.arrayList(lockedItems.size());
    loadPrimaryItems(lockedItems, reader, registry, primary);
    if (primary.isEmpty()) return null;
    return new WindowItemEditor<FrameBuilder>(prepare, WindowItemEditor.frame(context, getFrameId())) {
      @Override
      protected void setupWindow(FrameBuilder frame, EditLifecycleImpl editLife) throws CantPerformException {
        MetaInfo metaInfo = ItemActionUtils.getUniqueMetaInfo(primary);
        prepareEditor(context, primary, metaInfo);
        frame.setActionScope("WorkflowEditor");
        Configuration config = frame.getConfiguration().getOrCreateSubset("editor");
        ItemEditorUi editor = createEditor(frame, metaInfo, primary, config);
        frame.setContent(new EditorContent(editor, primary, editLife));
        setCloseConfirmation(editLife, editor);
        tuneFrame(frame);
      }
    };
  }

  private static void loadPrimaryItems(LongList lockedItems, DBReader reader, ItemModelRegistry registry, List<ItemUiModelImpl> primary) {
    for (LongListIterator i = lockedItems.iterator(); i.hasNext();) {
      long lockedItem = i.next();
      Long itemType = reader.getValue(lockedItem, DBAttribute.TYPE);
      if (itemType == null) continue;
      if (Boolean.TRUE.equals(reader.getValue(itemType, SyncAttributes.IS_PRIMARY_TYPE))) {
        ItemUiModelImpl model = registry.createNewModel(lockedItem, reader);
        if (model != null) {
          primary.add(model);
        }
      }
      // todo :refactoring: assure that it's enough: that slaves that are added to lock are loaded this way
    }
  }

  /**
   * Implement this method if editor needs to retrieve some additional data.
   * @items readonly
   */
  protected void prepareEditor(ActionContext context, List<? extends ItemWrapper> items, MetaInfo metaInfo) throws CantPerformException {
  }

  /**
   * @param items Note that we're not going to add any items to the list, so casting to List&lt;ItemWrapper&gt; is ok
   */
  @NotNull
  protected ItemEditorUi createEditor(@Nullable FrameBuilder frame, MetaInfo metaInfo, List<? extends ItemWrapper> items, Configuration configuration) throws CantPerformException {
    final List<? extends EditPrimitive> primitives = CantPerformException.ensureNotNull(
      getActionFields((List) items, configuration)
    );
    WorkflowActionItemEditor result = new WorkflowActionItemEditor(primitives, metaInfo, items) {
      protected void copyAdditionalValues(ItemUiModel model) {
        copyActionDataToModel(model, primitives);
      }
    };

    if (frame != null) {
      frame.setTitle(getActionTitle((List) items) + " - " + Local.text(Terms.key_Deskzilla));
      frame.setInitialFocusOwner(result.getInitialFocusOwner());
    }

    return result;
  }

  private static void setCloseConfirmation(EditLifecycleImpl editLife, final ItemEditorUi editor) {
    editLife.setDiscardConfirmation(new DefaultCloseConfirmation(false) {
      protected boolean isCloseConfirmationRequired(ActionContext _) throws CantPerformException {
        return editor.isConsiderablyModified();
      }

      @Override
      protected String getQuestion() {
        return L.content("Are you sure you want to cancel workflow action?");
      }
    });
  }

  protected void tuneFrame(FrameBuilder frame) {
  }

  protected abstract String getFrameId();

  protected abstract String getActionTitle(List<ItemWrapper> items);

  @Nullable
  protected abstract List<? extends EditPrimitive> getActionFields(List<ItemWrapper> items, Configuration configuration) throws CantPerformException;

  protected void copyActionDataToModel(ItemUiModel model, List<? extends EditPrimitive> primitives) {}
}
