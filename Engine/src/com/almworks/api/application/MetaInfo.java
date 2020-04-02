package com.almworks.api.application;

import com.almworks.api.edit.ItemCreator;
import com.almworks.api.engine.Connection;
import com.almworks.api.gui.MainMenu;
import com.almworks.engine.gui.ItemTableBuilder;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.collections.Convertor;
import com.almworks.util.components.AToolbar;
import com.almworks.util.components.ToolbarBuilder;
import com.almworks.util.config.Configuration;
import com.almworks.util.threads.ThreadAWT;
import com.almworks.util.ui.ElementViewer;
import com.almworks.util.ui.actions.*;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : Dyoma
 */
public interface MetaInfo {
  Registry REGISTRY = Registry.REGISTRY;
  
  TypedKey<MetaInfo> ASPECT_KEY = TypedKey.create("MetaInfo");
  String ATTACH_FILE = "ArtifactAction.AttachFile";
  String ATTACH_SCREENSHOT = MainMenu.Tools.SCREENSHOT;
  String ADD_COMMENT = "ArtifactAction.AddComment";
  String EDIT_COMMENT = "ArtifactAction.EditComment";
  String REPLY_TO_COMMENT = "ArtifactAction.ReplyToComment";
  String AUTO_ASSIGN = "ArtifactAction.AutoAssign";
  String REMOVE_COMMENT = "ArtifactAction.RemoveComment";
  String CREATE_LINK = "ArtifactAction.CreateLink";
  String RESOLVE_AS_DUPLICATE = "ArtifactAction.ResolveAsDuplicate";
  String WATCH_ARTIFACT = "ArtifactAction.WatchArtifact";
  String VOTE_FOR_ARTIFACT = "ArtifactAction.VoteForArtifact";
  String ASSIGN_ARTIFACT = "ArtifactAction.Assign";
  String MOVE_ARTIFACT = "ArtifactAction.Move";

  Convertor<MetaInfo, AListModel<ItemsTreeLayout>> TREE_LAYOUTS = new Convertor<MetaInfo, AListModel<ItemsTreeLayout>>() {
    public AListModel<ItemsTreeLayout> convert(MetaInfo metaInfo) {
      return metaInfo.getTreeLayouts();
    }
  };

  Collection<ModelKey<?>> getKeys();

  ElementViewer<ItemUiModel> createViewer(Configuration config);

  String getTypeId();

  ItemCreator newCreator(Configuration configuration);

  @Deprecated
  List<? extends AnAction> getActions();

  @Nullable
  ToolbarBuilder getToolbarBuilder(boolean singleItem);

  @ThreadAWT
  ElementViewer<ItemUiModel> createEditor(Configuration config);

  LongList getSlavesToLockForEditor(ItemWrapper editedItem);

  /**
   * This method is invoked inside read transaction in the started edit to check for slaves that appeared after UI had been reloaded last time.
   */
  LongList getSlavesToLockForEditor(ItemVersion itemVersion);

  ModelKey<Boolean> getEditBlockKey();

  /**
   * Use static model key instance if you know what key you want to get.
   * This method can be useful even in presence of statically available model keys, e.g. for connecting controllers to fields by their names specified in GUI form builder 
   */
  <T extends ModelKey<?>> T findKey(String name);

  <T extends ModelKey<?>> T findKeyByAttribute(DBAttribute<?> attribute, Connection connection);

  String getDisplayableType();

  AListModel<ItemsTreeLayout> getTreeLayouts();

  boolean canImport(ActionContext context, ItemWrapper target, List<ItemWrapper> items) throws
    CantPerformException;

  void importItems(ItemWrapper target, List<? extends ItemWrapper> items, ActionContext context) throws CantPerformException;

  @Nullable
  AnAction getStdAction(String id, ActionContext context) throws CantPerformException;

  void addLeftFields(ItemTableBuilder builder);

  String getPartialDownloadHtml();

  /**
   * Add additional item editor actions to be placed on the top of editor window, next to Save, Commit, Discard.
   */
  void setupEditToolbar(AToolbar toolbar);

  @ThreadAWT
  @NotNull
  VerifierManager getVerifierManager();

  class Registry {
    private static final Registry REGISTRY = new Registry();

    private final Map<DBItemType, MetaInfo> myMetaInfos =
      new ConcurrentHashMap<DBItemType, MetaInfo>(1);

    public MetaInfo getMetaInfo(long item, DBReader reader) {
      Long kind = DBAttribute.TYPE.getValue(item, reader);
      if (kind == null)
        return null;
      for (Map.Entry<DBItemType, MetaInfo> e : myMetaInfos.entrySet()) {
        if (kind == reader.findMaterialized(e.getKey())) {
          return e.getValue();
        }
      }
      return null;
    }

    public MetaInfo getMetaInfo(ItemVersion version) {
      return getMetaInfo(version.getItem(), version.getReader());
    }

    public void registerMetaInfo(DBItemType type, MetaInfo info) {
      myMetaInfos.put(type, info);
    }
  }
}
