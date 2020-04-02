package com.almworks.actions;

import com.almworks.api.actions.BaseEditAction;
import com.almworks.api.actions.EditorWindowCreator;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.edit.WindowItemEditor;
import com.almworks.api.explorer.ItemModelRegistry;
import com.almworks.api.gui.BasicWindowBuilder;
import com.almworks.api.gui.MainMenu;
import com.almworks.edit.EditLifecycleImpl;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.integers.util.LongListConcatenation;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.EditPrepare;
import com.almworks.items.sync.ItemEditor;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;
import org.jetbrains.annotations.*;

import java.util.List;

/**
 * @author dyoma
 */
class EditPrimaryItemAction extends BaseEditAction.EditInWindowAction {
  protected EditPrimaryItemAction() {
    super(null, Icons.ACTION_EDIT_ARTIFACT, SINGLE_ITEM);
    setDefaultText(PresentationKey.NAME, "&Edit " + Terms.ref_Artifact);
    setDefaultText(PresentationKey.SHORT_DESCRIPTION, L.tooltip("Edit selected " + Terms.ref_artifact));
    setDefaultPresentation(PresentationKey.ENABLE, EnableState.DISABLED);
  }

  @NotNull
  @Override
  public EditorWindowCreator getWindowCreator() {
    return myEditor;
  }

  private final EditorWindowCreator myEditor = new EditorWindowCreator() {
    @NotNull
    @Override
    public LongList getItemsToLock(List<ItemWrapper> primaryItems, ActionContext context) throws CantPerformException {
      ItemWrapper primary = primaryItems.get(0);
      LongArray lockPrimary = LongArray.create(primary.getItem());
      LongList lockSlaves = primary.getMetaInfo().getSlavesToLockForEditor(primary);
      return new LongListConcatenation(lockPrimary, lockSlaves);
    }

    @Override
    public ItemEditor createItemEditor(@NotNull LongList lockedItems, @NotNull List<ItemWrapper> primaryItems, @NotNull DBReader reader, @NotNull EditPrepare prepare, @NotNull ItemModelRegistry registry, @NotNull final ActionContext context) {
      ItemWrapper primaryOld = primaryItems.get(0);
      if (!tryAddAppearedSlavesToLock(primaryOld, reader, prepare))
        return null;

      final ItemUiModelImpl model = registry.createNewModel(primaryOld.getItem(), reader);
      return model == null ? null : new WindowItemEditor(prepare, WindowItemEditor.frame(context, "editItem")) {
        @Override
        protected void setupWindow(BasicWindowBuilder builder, EditLifecycleImpl editLife) throws CantPerformException {
          editLife.setupEditModelWindow(builder, model, new IdActionProxy(MainMenu.ItemEditor.SAVE_DRAFT));
          builder.setTitle(L.frame(Local.parse("Edit " + Terms.ref_Artifact + " - " + Terms.ref_Deskzilla)));
          builder.setActionScope("ArtifactEditor");
          builder.setContent(PrimaryItemEditor.editModel(model,builder.getConfiguration()));
        }
      };
    }
  };

  private static boolean tryAddAppearedSlavesToLock(ItemWrapper primaryOld, DBReader reader, EditPrepare prepare) {
    return prepare.addItems(primaryOld.getMetaInfo().getSlavesToLockForEditor(SyncUtils.readTrunk(reader, primaryOld.getItem())));
  }
}
