package com.almworks.api.application.util;

import com.almworks.api.application.*;
import com.almworks.api.application.field.ItemField;
import com.almworks.api.edit.ItemCreator;
import com.almworks.api.engine.Connection;
import com.almworks.engine.gui.ItemTableBuilder;
import com.almworks.items.api.DBAttribute;
import com.almworks.spi.provider.ConnectionContext;
import com.almworks.tags.TagsComponent;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.components.AToolbar;
import com.almworks.util.components.ToolbarBuilder;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.Context;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.threads.ThreadAWT;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.ElementViewer;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author dyoma
 */
public abstract class BaseMetaInfo implements MetaInfo {
  private final Collection<ModelKey<?>> myKeys;
  private final Map<DBAttribute, ModelKey<?>> myKeysMap;
  private final List<AnAction> myActions;

  public BaseMetaInfo(Collection<? extends ModelKey<?>> keys, Map<DBAttribute, ModelKey<?>> keyMap,
    List<? extends AnAction> actions)
  {
    keys = addGlobalKeys(keys);
    myKeys = Collections.unmodifiableCollection(keys);
    myActions = Collections.unmodifiableList(actions);
    myKeysMap = Collections15.unmodifiableMapCopy(keyMap);
  }

  private static Collection<? extends ModelKey<?>> addGlobalKeys(Collection<? extends ModelKey<?>> keys) {
    List<ModelKey<?>> result = Collections15.arrayList(keys);
    result.add(TagsModelKey.INSTANCE);
    return result;
  }

  public Collection<ModelKey<?>> getKeys() {
    return myKeys;
  }

  public <T extends ModelKey<?>> T findKey(String name) {
    return (T) ModelKey.GET_NAME.detectEqual(myKeys, name);
  }

  public <T extends ModelKey<?>> T findKeyByAttribute(DBAttribute<?> attribute, Connection connection) {
    for (Map.Entry<DBAttribute, ModelKey<?>> e : myKeysMap.entrySet()) {
      if (attribute.equals(e.getKey()))
        return (T) e.getValue();
    }
    ConnectionContext connectionContext = connection.getContext();
    ItemField field = connectionContext.getCustomFields().getByAttribute(attribute);
    if (field != null) {
      return (T) field.getModelKey();
    }
    TagsComponent tags = Context.get(TagsComponent.class);
    if (tags != null && TagsComponent.TAGS.equals(attribute)) {
      return (T) TagsModelKey.INSTANCE;
    }
    return null;
  }

  public List<? extends AnAction> getActions() {
    return myActions;
  }

  public ToolbarBuilder getToolbarBuilder(boolean singleItem) {
    return null;
  }

  public ElementViewer<ItemUiModel> createViewer(Configuration config) {
    return new ElementViewer.Empty<ItemUiModel>();
  }

  public ItemCreator newCreator(Configuration configuration) {
    return new ItemCreator() {
      private final ElementViewer<ItemUiModel> myEditor = new ElementViewer.Empty<ItemUiModel>();

      public ElementViewer<ItemUiModel> getEditor() {
        return myEditor;
      }

      public void prepareModel(ItemUiModel model) {
      }

      public void valueCommitted(PropertyMap propertyMap) {
      }

      public void setupToolbar(AToolbar toolbar) {

      }
    };
  }

  @Override
  public ElementViewer<ItemUiModel> createEditor(Configuration config) {
    Threads.assertAWTThread();
    return new ElementViewer.Empty<ItemUiModel>();
  }

  public AListModel<ItemsTreeLayout> getTreeLayouts() {
    return AListModel.EMPTY;
  }

  public boolean canImport(ActionContext context, ItemWrapper target, List<ItemWrapper> items)
    throws CantPerformException
  {
    return false;
  }

  public void importItems(ItemWrapper target, List<? extends ItemWrapper> items, ActionContext context) throws CantPerformException {
    assert false;
  }

  @Nullable
  public AnAction getStdAction(String id, ActionContext context) throws CantPerformException {
    ActionRegistry registry = context.getSourceObject(ActionRegistry.ROLE);
    return getStdAction(id, registry);
  }

  @Nullable
  protected AnAction getStdAction(String id, ActionRegistry registry) throws CantPerformException {
    return null;
  }

  public void addLeftFields(ItemTableBuilder builder) {
  }

  @ThreadAWT
  @NotNull
  @Override
  public VerifierManager getVerifierManager() {
    return VerifierManager.TO_BE_IMPLEMENTED;
  }
}
