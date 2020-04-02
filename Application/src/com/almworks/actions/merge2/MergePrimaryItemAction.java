package com.almworks.actions.merge2;

import com.almworks.api.actions.ItemActionUtils;
import com.almworks.api.application.*;
import com.almworks.api.container.MutableComponentContainer;
import com.almworks.api.edit.ItemSyncSupport;
import com.almworks.api.edit.WindowItemEditor;
import com.almworks.api.explorer.ItemModelRegistry;
import com.almworks.api.gui.*;
import com.almworks.edit.EditLifecycleImpl;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.integers.util.LongListConcatenation;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.ShadowVersionSource;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.config.Configuration;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.actions.*;

public class MergePrimaryItemAction extends SimpleAction {
  public MergePrimaryItemAction() {
    super(L.actionName("&Merge\u2026"), Icons.MERGE_ACTION);
    setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, L.tooltip("Merge local changes with conflicting remote changes"));
  }

  @Override
  protected void customUpdate(UpdateContext context) throws CantPerformException {
    ItemWrapper item = ItemActionUtils.updateForEditSingleItem(context, ItemWrapper.ITEM_WRAPPER);
    LoadedItem.DBStatus dbStatus = item.getDBStatus();
    if (dbStatus != ItemWrapper.DBStatus.DB_CONFLICT) {
      context.setEnabled(EnableState.INVISIBLE);
      return;
    }
    MetaInfo metaInfo = item.getMetaInfo();
    LongList slaves = metaInfo.getSlavesToLockForEditor(item);
    ItemActionUtils.checkNotLocked(context, slaves);
  }

  @Override
  protected void doPerform(ActionContext context) throws CantPerformException {
    ItemWrapper wrapper = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    startEdit(wrapper, context);
  }

  private void startEdit(final ItemWrapper wrapper, ActionContext context)
    throws CantPerformException
  {
    final MetaInfo metaInfo = wrapper.getMetaInfo();
    final LongList slaves = metaInfo.getSlavesToLockForEditor(wrapper);
    SyncManager syncManager = context.getSourceObject(SyncManager.ROLE);
    final long primary = wrapper.getItem();
    LongListConcatenation allItems = new LongListConcatenation(LongArray.create(primary), slaves);
    final EditControl editControl = ItemSyncSupport.prepareEditOrShowLockingEditor(syncManager, allItems);
    final WindowManager manager = context.getSourceObject(WindowManager.ROLE);
    final ItemModelRegistry modelReg = context.getSourceObject(ItemModelRegistry.ROLE);
    editControl.start(new EditorFactory() {
      @Override
      public ItemEditor prepareEdit(DBReader reader, final EditPrepare prepare) {
        if (!prepare.addItems(metaInfo.getSlavesToLockForEditor(SyncUtils.readTrunk(reader, primary)))) return null;
        ItemVersion conflictVersion = ShadowVersionSource.conflict(reader).forItem(primary);
        ItemVersion baseVersion = ShadowVersionSource.base(reader).forItem(primary);
        PropertyMap conflict = modelReg.extractValues(conflictVersion);
        PropertyMap base = modelReg.extractValues(baseVersion);
        ItemUiModelImpl trunk = modelReg.createNewModel(primary, reader);
        if (trunk == null || base == null || conflict == null)
          return null;
        return new MergeEditor(manager, editControl, trunk, base, conflict);
      }
    });
  }

  public static void registerActions(ActionRegistry registry) {
    registry.registerAction(MainMenu.Edit.MERGE, new MergePrimaryItemAction());
  }

  private class MergeEditor extends WindowItemEditor<FrameBuilder> {
    private final ItemUiModelImpl myTrunk;
    private final PropertyMap myBase;
    private final PropertyMap myConflict;

    public MergeEditor(WindowManager manager, EditControl edit, ItemUiModelImpl trunk, PropertyMap base, PropertyMap conflict) {
      super(edit, frame(manager, "mergeItem"));
      myTrunk = trunk;
      myBase = base;
      myConflict = conflict;
    }

    @Override
    protected void setupWindow(FrameBuilder builder, EditLifecycleImpl editLife) throws CantPerformException {
      builder.addProvider(SimpleProvider.withData(ItemUiModelImpl.ROLE, myTrunk));
      Configuration config = builder.getConfiguration().createSubset("mergeConfig");
      builder.setTitle(L.frame(Local.parse("Merge " + Terms.ref_Artifact + " - " + Terms.ref_Deskzilla)));
      MergeComponent component = new MergeComponent(config, myTrunk, myBase, myConflict);
      component.initUI(editLife);
      MutableComponentContainer container = builder.getWindowContainer();
      container.reregisterActor(MergeComponent.ROLE, component);
      builder.setContent(component);
    }
  }
}
