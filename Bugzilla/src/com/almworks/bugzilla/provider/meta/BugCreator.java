package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.*;
import com.almworks.api.container.ContainerPath;
import com.almworks.api.edit.ItemCreator;
import com.almworks.api.explorer.gui.AbstractComboBoxModelKey;
import com.almworks.api.explorer.util.ItemKeys;
import com.almworks.api.store.Store;
import com.almworks.api.store.StoreUtils;
import com.almworks.bugzilla.gui.attachments.BugzillaAttachmentsController;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.BugzillaEnv;
import com.almworks.bugzilla.integration.data.ComponentDefaults;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.bugzilla.provider.meta.groups.BugGroupInfo;
import com.almworks.items.api.DBAttribute;
import com.almworks.store.StoreComponent;
import com.almworks.util.Env;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.AToolbar;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.io.persist.*;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.threads.*;
import com.almworks.util.ui.ElementViewer;
import com.almworks.util.ui.actions.PresentationMapping;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author dyoma
 */
class BugCreator implements ItemCreator {
  private static final boolean IGNORE_PRODUCT_DEFAULTS = Env.getBoolean(BugzillaEnv.IGNORE_PRODUCT_DEFAULTS);
  private static final ContainerPath NEW_BUG_DEFAULTS = new ContainerPath(null, "BugzillaBugCreator");
  private static final String PRODUCT_DEPENDENT_VALUES = "defaultValues";
  private static final String INDEPENDENT_VALUES = "lastProduct";
  private static final AbstractComboBoxModelKey[] PRODUCT_DEPENDENT_KEYS = new AbstractComboBoxModelKey[] {BugzillaKeys.component, BugzillaKeys.version, BugzillaKeys.milestone};
  private static final AbstractComboBoxModelKey[] INDEPENDENT_KEYS = new AbstractComboBoxModelKey[] {BugzillaKeys.product, BugzillaKeys.os, BugzillaKeys.platform, BugzillaKeys.priority, BugzillaKeys.severity};

  private final ElementViewer<ItemUiModel> myEditor;

  public BugCreator(ElementViewer<ItemUiModel> editor) {
    myEditor = editor;
  }

  public ElementViewer<ItemUiModel> getEditor() {
    return myEditor;
  }

  @CanBlock
  public void prepareModel(ItemUiModel model) {
    model.getModelMap().put(NEW_ITEM, true);
    LoadedItemServices services = model.services();
    Store store = getStore(services);

    final ModelMap modelsMap = model.getModelMap();

    prepareIndependentKeys(store, modelsMap);
    prepareDependentKeys(model, store, modelsMap);

    prepareComponentDefaults(model, services, modelsMap);
  }

  private void prepareComponentDefaults(ItemUiModel model, LoadedItemServices itemServices, ModelMap modelsMap) {
    BugzillaContext context = BugzillaUtil.getContext(itemServices);
    if (context != null) {
      ComponentDefaultsTracker defaultsTracker = context.getComponentDefaultsTracker();
      ComponentDefaultsListener listener = new ComponentDefaultsListener(modelsMap, defaultsTracker);
      model.addAWTChangeListener(Lifespan.FOREVER, listener);
      ThreadGate.AWT.execute(listener);
    }
  }

  private void prepareDependentKeys(ItemUiModel model, Store store, ModelMap modelsMap) {
    Map<String, Map<String, String>> defaultValues = loadDefaults(store).access();
    ProductChangeListeners listener = new ProductChangeListeners(modelsMap, defaultValues);
    listener.execute();
    model.addAWTChangeListener(Lifespan.FOREVER, listener);
  }

  private static void prepareIndependentKeys(Store store, ModelMap modelsMap) {
    Map<String, String> independentValues = loadIndependentField(store).access();
    for (AbstractComboBoxModelKey key : INDEPENDENT_KEYS) {
      String value = independentValues.get(key.getName());
      if (value == null)
        continue;
      key.setModelValue(modelsMap, ItemKeys.createItemKey(value));
    }
  }

  private static Persistable<Map<String, Map<String, String>>> loadDefaults(Store store) {
    PersistableHashMap<String, Map<String, String>> persistableMap = PersistableHashMap.create(new PersistableString(),
      new PersistableHashMap(new PersistableString(), new PersistableString()));
    boolean success = StoreUtils.restorePersistable(store, PRODUCT_DEPENDENT_VALUES, persistableMap);
    if (!success)
      persistableMap.set(Collections15.<String, Map<String, String>>hashMap());
    return persistableMap;
  }

  private static Persistable<Map<String, String>> loadIndependentField(Store store) {
    PersistableHashMap<String, String> persistableMap =
      PersistableHashMap.create(new PersistableString(), new PersistableString());
    boolean success = StoreUtils.restorePersistable(store, INDEPENDENT_VALUES, persistableMap);
    if (!success) {
      persistableMap.set(Collections15.<String, String>hashMap());
    }
    return persistableMap;
  }

  private static Store getStore(LoadedItemServices itemServices) {
    return itemServices.getService(StoreComponent.class).selectImplementation(NEW_BUG_DEFAULTS);
  }

  public void valueCommitted(final PropertyMap propertyMap) {
    ThreadGate.LONG(BugCreator.class).execute(new Runnable() {
      public void run() {
        ItemKey product = BugzillaKeys.product.getValue(propertyMap);
        if (product == null)
          return;
        Store store = getStore(LoadedItemServices.VALUE_KEY.getValue(propertyMap));
        Persistable<Map<String, String>> independentPersistable = loadIndependentField(store);
        Map<String, String> independentValues = independentPersistable.access();
        for (AbstractComboBoxModelKey key : INDEPENDENT_KEYS) {
          independentValues.put(key.getName(), key.getValue(propertyMap).getId());
        }
        StoreUtils.storePersistable(store, INDEPENDENT_VALUES, independentPersistable);

        Persistable<Map<String, Map<String, String>>> defaults = loadDefaults(store);
        Map<String, Map<String, String>> loaded = defaults.access();
        loaded.put(product.getId(), getCurrentValues(propertyMap));
        StoreUtils.storePersistable(store, PRODUCT_DEPENDENT_VALUES, defaults);
      }
    });
  }

  public void setupToolbar(AToolbar toolbar) {
    final Map<String, PresentationMapping<?>> mapping = PresentationMapping.VISIBLE_NONAME;
    toolbar.addAction(BugzillaAttachmentsController.ATTACH_FILE).overridePresentation(mapping);
    toolbar.addAction(BugzillaAttachmentsController.ATTACH_SCREENSHOT).overridePresentation(mapping);
  }

  private static Map<String, String> getCurrentValues(PropertyMap values) {
    Map<String, String> result = Collections15.hashMap();
    for (AbstractComboBoxModelKey key : PRODUCT_DEPENDENT_KEYS) {
      ItemKey value = key.getValue(values);
      if (value == null)
        continue;
      result.put(key.getName(), value.getId());
    }
    return result;
  }

  private class ProductChangeListeners implements ChangeListener, Runnable {
    private final Map<String, Map<String, String>> myDefaultValues;
    private ItemKey myLastProduct;
    private final ModelMap myModelsMap;

    public ProductChangeListeners(ModelMap modelsMap, Map<String, Map<String, String>> defaultValues) {
      myModelsMap = modelsMap;
      myDefaultValues = defaultValues;
      myLastProduct = getProduct();
    }

    public void onChange() {
      ItemKey currentProduct = getProduct();
      if (Util.equals(myLastProduct, currentProduct))
        return;
      myLastProduct = currentProduct;
      execute();
    }

    private void execute() {
      ThreadGate.AWT.execute(this);
    }

    public void run() {
      loadProductDefaults();
    }

    private void loadProductDefaults() {
      if (myLastProduct == null)
        return;
      Map<String, String> productsDefaults = myDefaultValues.get(myLastProduct.getId());
      ProductDependenciesTracker tracker = ProductDependenciesKey.KEY.getValue(myModelsMap);
      ProductDependencyInfo depInfo = tracker == null ? null : tracker.getInfo(myLastProduct);

      if (productsDefaults == null) {
        if (depInfo != null) {
          setAnyValid(depInfo, BugzillaKeys.component, BugzillaAttribute.COMPONENT);
          setAnyValid(depInfo, BugzillaKeys.version, BugzillaAttribute.VERSION);
          setAnyValid(depInfo, BugzillaKeys.milestone, BugzillaAttribute.TARGET_MILESTONE);
        }
      } else {
        MetaInfo metaInfo = myModelsMap.getMetaInfo();
        for (Map.Entry<String, String> entry : productsDefaults.entrySet()) {
          String eKey = entry.getKey();
          String eValue = entry.getValue();
          AbstractComboBoxModelKey key = metaInfo.findKey(eKey);
          key.setModelValue(myModelsMap, ItemKeys.createItemKey(eValue));
        }
      }

      if (depInfo != null) {
        updateModelWithGroupInfo(depInfo);
        updateModelWithDefaultValues(depInfo);
      }
    }

    private void updateModelWithDefaultValues(ProductDependencyInfo depInfo) {
      if (IGNORE_PRODUCT_DEFAULTS)
        return;
      Map<DBAttribute, ResolvedItem> defaultValues = depInfo.getDefaultValues();
      if (defaultValues == null || defaultValues.isEmpty())
        return;
      Collection<? extends ModelKey> keys = myModelsMap.getAllKeys();
      PropertyMap props = new PropertyMap();
      for (Map.Entry<DBAttribute, ResolvedItem> entry : defaultValues.entrySet()) {
        for (ModelKey key : keys) {
          if (key instanceof ComboBoxModelKey) {
            ComboBoxModelKey comboKey = (ComboBoxModelKey) key;
            if (entry.getKey().equals(comboKey.getAttribute())
                && !comboKey.isUserModified(myModelsMap))
            {
              comboKey.takeSnapshot(props, myModelsMap);
              comboKey.setValue(props, entry.getValue());
              comboKey.copyValue(myModelsMap, props);
              props.clear();
              break;
            }
          }
        }
      }
    }

    private void updateModelWithGroupInfo(ProductDependencyInfo depInfo) {
      BugGroupInfo newGroups = new BugGroupInfo(depInfo.getDefaultGroups());
      ModelKey<BugGroupInfo> key = BugzillaKeys.groups;
      PropertyMap props = new PropertyMap();
      key.takeSnapshot(props, myModelsMap);
      BugGroupInfo oldGroups = key.getValue(props);
      newGroups.applyChangesFrom(oldGroups);
      key.setValue(props, newGroups);
      key.copyValue(myModelsMap, props);
    }

    private void setAnyValid(ProductDependencyInfo info, ComboBoxModelKey key, BugzillaAttribute attribute) {
      List<ItemKey> variants = key.getVariantsList(myModelsMap);
      ItemKey value = info.selectAnyValid(variants, attribute);
      if (value != null)
        key.setModelValue(myModelsMap, value);
    }

    private ItemKey getProduct() {
      return BugzillaKeys.product.getValue(myModelsMap);
    }
  }


  private class ComponentDefaultsListener implements ChangeListener, Runnable {
    private ItemKey myLastProduct;
    private ItemKey myLastComponent;
    private final ModelMap myModelsMap;
    private final ComponentDefaultsTracker myDefaultsTracker;
    private boolean myUpdating;

    public ComponentDefaultsListener(ModelMap modelsMap, ComponentDefaultsTracker defaultsTracker) {
      myModelsMap = modelsMap;
      myDefaultsTracker = defaultsTracker;
    }

    @ThreadAWT
    public void onChange() {
      if (myUpdating)
        return;
      update();
    }

    @ThreadAWT
    private void update() {
      Threads.assertAWTThread();
      ComponentDefaults defaults = getCurrentDefaults();
      if (defaults == null)
        return;

      PropertyMap props = new PropertyMap();
      BugzillaKeys.assignedTo.takeSnapshot(props, myModelsMap);
      BugzillaKeys.qaContact.takeSnapshot(props, myModelsMap);
      BugzillaKeys.cc.takeSnapshot(props, myModelsMap);

      updateWithKey(props, BugzillaKeys.assignedTo, defaults.getDefaultAssignee());
      updateWithKey(props, BugzillaKeys.qaContact, defaults.getDefaultQA());
      updateCC(props, defaults.getDefaultCC());

      myUpdating = true;
      try {
        copyIfNotModified(BugzillaKeys.assignedTo, myModelsMap, props);
        copyIfNotModified(BugzillaKeys.qaContact, myModelsMap, props);
        copyIfNotModified(BugzillaKeys.cc, myModelsMap, props);
      } finally {
        myUpdating = false;
      }
    }

    @Nullable
    private ComponentDefaults getCurrentDefaults() {
      ItemKey currentProduct = BugzillaKeys.product.getValue(myModelsMap);
      ItemKey currentComponent = BugzillaKeys.component.getValue(myModelsMap);
      if (currentProduct == null || currentComponent == null || currentProduct.equals(myLastProduct) && currentComponent.equals(myLastComponent))
        return null;
      myLastProduct = currentProduct;
      myLastComponent = currentComponent;

      return myDefaultsTracker.getDefaults(myLastProduct.getId(), myLastComponent.getId());
    }

    private void copyIfNotModified(ModelKey<?> key, ModelMap to, PropertyMap from) {
      if(key instanceof UserModifiable && ((UserModifiable)key).isUserModified(to)) {
        return;
      }
      key.copyValue(to, from);
    }

    private void updateCC(PropertyMap props, List<String> cclist) {
      if (cclist != null) {
        List<ItemKey> akeylist = Collections15.arrayList();
        for (String value : cclist) {
          if (value == null)
            continue;
          ItemKey akey = keyFromValue(value);
          if (akey == ItemKeyStub.ABSENT)
            continue;
          akeylist.add(akey);
        }
        BugzillaKeys.cc.setValue(props, akeylist);
      }
    }

    private void updateWithKey(PropertyMap props, ModelKey<ItemKey> key, String value) {
      if (value != null) {
        ItemKey akey = keyFromValue(value);
        key.setValue(props, akey);
      }
    }

    private ItemKey keyFromValue(String value) {
      return value.length() == 0 ? ItemKeyStub.ABSENT : User.userResolver.getItemKey(value);
    }

    public void run() {
      update();
    }
  }
}
