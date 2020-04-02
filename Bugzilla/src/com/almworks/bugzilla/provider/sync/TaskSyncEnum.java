package com.almworks.bugzilla.provider.sync;

import com.almworks.api.application.ResolvedItem;
import com.almworks.api.connector.CancelledException;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.engine.SyncParameter;
import com.almworks.api.engine.SyncParameters;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.bugzilla.provider.OurConfiguration;
import com.almworks.bugzilla.provider.datalink.BugzillaAttributeLink;
import com.almworks.bugzilla.provider.datalink.ReferenceLink;
import com.almworks.bugzilla.provider.datalink.schema.*;
import com.almworks.bugzilla.provider.datalink.schema.custom.CustomField;
import com.almworks.engine.items.DatabaseUnwrapper;
import com.almworks.integers.LongArray;
import com.almworks.items.api.*;
import com.almworks.items.sync.*;
import com.almworks.util.L;
import com.almworks.util.collections.MultiMap;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * This task synchronizes enumerated values (products, platforms, keywords, etc)
 *
 * @author sereda
 */
class TaskSyncEnum extends LinearProgressTask {
  private volatile BugzillaLists myInfo;
  private final LinkedHashMap<String, BugzillaProductInformation> myProductInfo = Collections15.linkedHashMap();
  private volatile Map<String, String> myKeywords;
  @Nullable
  private volatile BugzillaRDFConfig myConfig;

  public TaskSyncEnum(SyncController controller) {
    super(controller, "load-enums", L.progress("Downloading value lists"), 2000, false);
  }

  public void doRun() throws ConnectorException {
    OurConfiguration config = getContext().getConfiguration().getValue();
    String[] products = config.getLimitingProducts();
    List<String> productsList = products.length == 0 ? null : Arrays.asList(products);
    BugzillaIntegration integration = getIntegration();
    myConfig = integration.loadRDFConfig(productsList);
    myInfo = integration.getBugzillaLists(myConfig, productsList);
    loadProductDefaults(integration);
    myKeywords = integration.loadKeywords();
    getContext().getActor(SyncManager.ROLE).writeDownloaded(new WriteDB());
  }

  public boolean isApplicable(SyncParameters syncParameters) {
    return syncParameters.get(SyncParameter.INITIALIZE_CONNECTION) != null ||
      syncParameters.get(SyncParameter.ALL_ITEMS) != null;
  }

  private void loadProductDefaults(BugzillaIntegration integration) throws ConnectorException {
    if (!integration.isAuthenticationAvailable())
      return;
    List<String> products = Collections15.arrayList(myInfo.getStringList(BugzillaAttribute.PRODUCT));

    OurConfiguration configuration = getContext().getConfiguration().getValue();
    if (configuration == null)
      return;
    String[] limitArray = configuration.getLimitingProducts();
    if (limitArray != null && limitArray.length > 0) {
      products.retainAll(Collections15.hashSet(limitArray));
    }

    boolean privacyAccessible = false;
    boolean privacyAccessibleKnown = false;
    boolean privacyWarned = false;

    int productCount = products.size();
    if (productCount > 0) {
      float step = 1F / (productCount + 1);
      float progress = step;
      for (String product : products) {
        progress(progress, "Downloading defaults for product " + product);
        progress += step;
        BugzillaProductInformation info = null;
        try {
          info = integration.getProductInformation(product);
        } catch (ConnectorException e) {
          Log.warn("cannot load defaults for product " + product);
          continue;
        }
        if (info == null) {
          assert false : product;
          continue;
        }
        myProductInfo.put(product, info);

        boolean privacy = info.isDescriptionMayBePrivate();
        if (privacyAccessibleKnown) {
          if (privacyAccessible != privacy && !privacyWarned) {
            Log.warn("description may be private in one product and may not be private in another");
            privacyWarned = true;
          }
        } else {
          privacyAccessible = privacy;
          privacyAccessibleKnown = true;
        }
      }
    }

    if (privacyAccessibleKnown) {
      getContext().setCommentPrivacyAccessible(privacyAccessible, "sync");
    }
  }

  private class WriteDB implements DownloadProcedure<DBDrain> {
    private final Map<BugzillaAttribute, Map<String, Long>> myStringMaps = Collections15.hashMap();

    @Override
    public void write(DBDrain drain) throws DBOperationCancelledException {
      try {
        updateEnums(drain);
        Product.updateDependencies(drain, TaskSyncEnum.this, myProductInfo, myStringMaps, myInfo);
        updateCustomFields(drain);
        updateUserList(drain);
        getPrivateMetadata().updateBugPrototype(drain);
        updateInitialStatuses();
        BugzillaRDFConfig config = myConfig;
        Map<String, String> keywords = new HashMap<>(Util.NN(myKeywords, Collections.emptyMap()));
        if (config != null) {
          getContext().getWorkflowTracker().reportStatusInfos(config.getStatusInfos());
          updateBugzillaVersion(drain, config);
          keywords.putAll(config.getKeywords());
        }
        Keywords.updateAll(drain, getPrivateMetadata(), keywords);
      } catch (CancelledException e) {
        throw new DBOperationCancelledException();
      }
    }

    private void updateBugzillaVersion(DBDrain drain, BugzillaRDFConfig config) {
      final ItemVersionCreator creator = drain.changeItem(getPrivateMetadata().getConnectionRef());
      creator.setValue(CommonMetadata.attrBugzillaVerison, config.getInstallVersion());
    }

    private void updateInitialStatuses() {
      if (myProductInfo != null && !myProductInfo.isEmpty()) {
        BugzillaProductInformation pi = myProductInfo.values().iterator().next();
        List<String> initialStatuses = pi.getInitialStatuses();
        if (initialStatuses != null && !initialStatuses.isEmpty()) {
          getContext().getWorkflowTracker().reportInitialStatuses(initialStatuses);
        }
      }
    }

    private void updateUserList(DBDrain drain) {
      Map<BugzillaUser, BugzillaUser> users = Collections15.hashMap();
      for (BugzillaProductInformation bugzillaProductInformation : myProductInfo.values()) {
        for (BugzillaUser user : bugzillaProductInformation.getUserList()) {
          BugzillaUser prev = users.get(user);
          if (prev != null) user = user.mergeWith(prev);
          users.put(user, user);
        }
      }
      User.setEnabledUsers(drain, users.keySet(), getPrivateMetadata());
    }

    private void updateCustomFields(DBDrain drain) {
      Map<String, CustomFieldInfo> infos = Collections15.hashMap();
      MultiMap<String, String> defaults = MultiMap.create();
      CustomFieldDependencies commondDeps = new CustomFieldDependencies(CustomFieldDependencies.Source.NEW_BUG);
      boolean strictInfos = false;
      if (myProductInfo != null && !myProductInfo.isEmpty()) {
        collectCommonInfos(infos, defaults, commondDeps, myProductInfo.values());
      }
      if (myConfig != null) {
        // override with information from config
        collectInfosFromConfig(infos, myConfig.getCustomFieldInfos());
        strictInfos = myConfig.isCustomFieldDataReliable();
      }
      CustomField.syncFields(drain, getPrivateMetadata(), strictInfos, infos, defaults, myInfo.getCustomFieldNames(), commondDeps);
    }

    private void collectInfosFromConfig(Map<String, CustomFieldInfo> infos, List<CustomFieldInfo> customFieldInfos) {
      for (CustomFieldInfo cfi : customFieldInfos) {
        mergeCustomFieldInfo(infos, cfi.getId(), cfi);
      }
    }

    private void mergeCustomFieldInfo(Map<String, CustomFieldInfo> infos, String cfid, CustomFieldInfo info) {
      CustomFieldInfo old = infos.get(cfid);
      if (old == null) {
        infos.put(cfid, info);
        return;
      }
      CustomFieldInfo merged = CustomFieldInfo.merge(old, info);
      if (merged != old) {
        infos.put(cfid, merged);
      }
    }

    private void collectCommonInfos(Map<String, CustomFieldInfo> infos, MultiMap<String, String> defaults,
      CustomFieldDependencies commondDeps, Collection<BugzillaProductInformation> productInfos)
    {
      for (BugzillaProductInformation productInformation : productInfos) {
        Map<String, CustomFieldInfo> mapi = productInformation.getCustomFieldInfo();
        if (mapi != null) {
          for (Map.Entry<String, CustomFieldInfo> e : mapi.entrySet()) {
            mergeCustomFieldInfo(infos, e.getKey(), e.getValue());
          }
        }
        MultiMap<String, String> mapd = productInformation.getCustomFieldDefaultValues();
        if (mapd != null) {
          if (defaults.isEmpty()) {
            defaults.addAll(mapd);
          } else {
            if (!Util.equals(mapd, defaults)) {
              Log.warn("custom field default values differs among products: " + defaults + "   " + mapd);
            }
          }
        }
        CustomFieldDependencies deps = productInformation.getCustomFieldDependencies();
        if (deps != null) {
          assert deps.getSource() == commondDeps.getSource() : deps.getSource() + " " + commondDeps.getSource();
          commondDeps.merge(deps);
        }
      }
    }

    private void updateEnums(DBDrain drain) throws CancelledException {
      Map<BugzillaAttribute, List<String>> lists = myInfo.getLists();
      int c = lists.size();
      float inc = c == 0 ? 0F : 1F / c;
      int i = 0;
      for (Map.Entry<BugzillaAttribute, List<String>> entry : lists.entrySet()) {
        checkCancelled();
        progress(inc * i);
        i++;
        BugzillaAttribute attribute = entry.getKey();
        List<String> values = entry.getValue();
        Map<String, Long> newStringMap = Collections15.hashMap();
        BugzillaAttributeLink attributeLink = CommonMetadata.ATTR_TO_LINK.get(attribute);
        if (!(attributeLink instanceof ReferenceLink)) {
          Log.debug("no reference link for attribute " + attribute + " [" + attributeLink + "]");
          continue;
        }
        ReferenceLink<?, String> link = (ReferenceLink) attributeLink;
        if (attribute == BugzillaAttribute.TARGET_MILESTONE) {
          values = adjustValuesOrder(values, myInfo.getProductDependencyMap(attribute));
        }
        updateEnum(link, values, newStringMap, drain);
        myStringMaps.put(attribute, newStringMap);
      }
    }

    private void updateEnum(final ReferenceLink<?, String> link, final List<String> values,
      final Map<String, Long> newStringMap, DBDrain drain) throws CancelledException
    {
      final DBFilter view = link.getReferentsView(getPrivateMetadata());
      BugzillaAttribute attribute = link.getBugzillaAttribute();
      Map<String, ItemVersionCreator> existing = getExistingEnumItems(link, view, drain);
      newStringMap.clear();
      int count = 0;
      for (String value : values) {
        checkCancelled();
        final int order = ++count;
        final String convertedValue = convertBugzillaValue(attribute, value);
        ItemVersionCreator enumValue = existing.remove(convertedValue);
        if (enumValue == null) enumValue = drain.changeItem(link.getOrCreateReferent(getPrivateMetadata(), convertedValue, drain));

        enumValue.setValue(ResolvedItem.attrOrder, order);
        newStringMap.put(convertedValue, enumValue.getItem());
      }
      Collection<ItemVersionCreator> removed = existing.values();
      for (ItemVersionCreator item : removed) item.delete();
    }

    private String convertBugzillaValue(BugzillaAttribute attribute, String value) {
      value = Util.NN(value).trim();
      if (attribute.isEmptyValue(value)) {
        return attribute.getEmptyValueName(value);
      } else {
        return value;
      }
    }

    private Map<String, ItemVersionCreator> getExistingEnumItems(ReferenceLink<?, String> link, DBFilter view, DBDrain drain) {
      // We need all items, even those that have been removed previously
      LongArray items = DatabaseUnwrapper.query(view, drain.getReader()).copyItemsSorted();
      Map<String, ItemVersionCreator> existing = Collections15.hashMap();
      DBAttribute<String> attr = link.getReferentUniqueKey();
      for (int i = 0; i < items.size(); i++) {
        ItemVersionCreator creator = drain.changeItem(items.get(i));
        String value = creator.getValue(attr);
        if (value == null) {
          Log.warn("removing enum revision with no key: " + creator + " " + link.getBugzillaAttribute());
          assert false;
          creator.delete();
          continue;
        }
        creator.setAlive();
        existing.put(value, creator);
      }
      return existing;
    }

    /**
     * Given random order of values (as it appears on the search form before a project is selected),
     * and per-product value mapping with correct order, tries to reorder full list to correspond to
     * each product's order. (Might fail if one project has order A, B and another has order B, A).
     */
    private List<String> adjustValuesOrder(List<String> values, Map<String, List<String>> productMap) {
      if (values == null || values.isEmpty() || productMap == null || productMap.isEmpty())
        return values;
      List<String> r = Collections15.arrayList(values);
      for (List<String> ordered : productMap.values()) {
        // go through each product map - the last one wins
        int lastIndex = -1;
        for (String value : ordered) {
          // go through each value and, if it appears before the last ordered one in the full list, move it ahead
          if ("---".equals(value) || "".equals(value) || value == null)
            continue;
          int k = r.indexOf(value);
          if (k < 0) {
            Log.warn(this + ": value [" + value + "] is not found in base list");
            continue;
          }
          if (k < lastIndex) {
            // move forward
            r.remove(k);
            r.add(lastIndex, value);
          } else {
            lastIndex = k;
          }
        }
      }
      return r;
    }

    @Override
    public void onFinished(DBResult<?> result) {
      if (result.isSuccessful()) getSyncData().logCommit(result.getCommitIcn());
      setDone();
    }
  }
}
