package com.almworks.actions.create;

import com.almworks.api.application.ItemUiModel;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.edit.CreateItemPolicy;
import com.almworks.api.edit.ItemCreator;
import com.almworks.api.explorer.ItemModelRegistry;
import com.almworks.api.gui.WindowController;
import com.almworks.edit.EditLifecycleImpl;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.items.api.*;
import com.almworks.util.*;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.i18n.Local;
import com.almworks.util.ui.ElementViewer;
import com.almworks.util.ui.actions.SimpleProvider;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

/**
 * @author : Dyoma
 */
class NewItemManager {
  private final Configuration myConfiguration;
  private final CreateItemPolicy myPolicy;
  private final Object myContextData;
  private final DBIdentifiedObject myType;
  private final WindowController myWindow;
  private final NewArtifactForm myForm;
  private final SimpleProvider myProvider = new SimpleProvider(ItemWrapper.ITEM_WRAPPER, ItemUiModelImpl.ROLE, ItemCreator.ROLE);
  private final ItemModelRegistry myRegistry;
  private final EditLifecycleImpl myLife;

  private NewItemManager(Configuration configuration, NewArtifactForm form,
    DBIdentifiedObject type, CreateItemPolicy policy, Object contextData, EditLifecycleImpl life, ItemModelRegistry registry,
    WindowController windowController) {
    myLife = life;
    assert policy != null;
    myConfiguration = configuration;
    myPolicy = policy;
    myContextData = contextData;
    myRegistry = registry;
    myType = type;
    assert myType != null;
    myForm = form;
    myWindow = windowController;
    assert myWindow != null;
    myWindow.addDataProvider(myProvider);
  }

  public static <T> void start(Configuration configuration, NewArtifactForm form,
    DBIdentifiedObject type, CreateItemPolicy<T> policy, T contextData, EditLifecycleImpl life, ItemModelRegistry registry, 
    WindowController windowController) {
    NewItemManager manager =
      new NewItemManager(configuration, form, type, policy, contextData, life, registry, windowController);
    manager.start();
  }

  private void start() {
    Database.require().readForeground(new ReadTransaction<Object>() {
      @Override
      public Object transaction(DBReader reader) throws DBOperationCancelledException {
        loadData(reader);
        return null;
      }
    });
  }

  private void loadData(DBReader reader) {
    Pair<Long, ? extends ItemCreator> pair = myPolicy.newCreator(myContextData, myConfiguration, reader);
    if (pair == null) {
      assert false;
      return;
    }
    final ItemCreator creator = pair.getSecond();
    Long prototype = pair.getFirst();
    if (creator == null || prototype == null || prototype <= 0L) {
      assert false : prototype;
      return;
    }
    final ItemUiModelImpl model = loadModelFromPrototype(reader, prototype);
    if (model == null) return;

    final long typeItem = myType != null ? reader.findMaterialized(myType) : 0L;
    final String typeName = typeItem <= 0L ? Local.text(Terms.key_artifact) : reader.getValue(typeItem, DBAttribute.NAME);
    final String title = L.frame("New " + English.capitalize(typeName) + " - " + Local.text(Terms.key_Deskzilla));
    creator.prepareModel(model);
    ThreadGate.AWT.execute(new Runnable() {
      public void run() {
        myLife.setupEditModel(model, CreateItemCopyAction.createSaveAction());
        myProvider.setSingleData(ItemCreator.ROLE, creator);
        onDataLoaded(title, creator, model);
      }
    });
  }

  @Nullable
  private ItemUiModelImpl loadModelFromPrototype(DBReader reader, long prototype) {
    ItemUiModelImpl model = myRegistry.createNewModel(prototype, reader);
    if (model == null) {
      long prototypeType = reader.getValue(prototype, DBAttribute.TYPE);
      Log.error("NIM: no model! my type: " + myType.getId() + "(" + reader.findMaterialized(myType) + "), prototype type: " + Util.NN(reader.getValue(prototypeType, DBAttribute.ID)) + "(" + prototypeType + ')');
      assert false;
    }
    return model;
  }

  private void onDataLoaded(String title, ItemCreator creator, ItemUiModelImpl model) {
    myWindow.setTitle(title);
    ElementViewer<ItemUiModel> editor = creator.getEditor();
    myForm.setEditor(editor, creator);
    myProvider.setSingleData(ItemUiModelImpl.ROLE, model);
    myProvider.setSingleData(ItemWrapper.ITEM_WRAPPER, model);
    editor.showElement(model);
  }
}
