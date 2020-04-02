package com.almworks.bugzilla.provider.datalink.schema;

import com.almworks.api.connector.CancelledException;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.sync.Task;
import com.almworks.integers.LongArray;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DP;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.sync.DBDrain;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.items.util.*;
import com.almworks.util.Pair;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.Convertors;
import com.almworks.util.collections.LongSet;
import com.almworks.util.commons.Condition;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

public class Product {
  public static final DBNamespace NS = BugzillaProvider.NS.subNs("product");
  public static final EnumType ENUM_PRODUCTS = EnumType.create(NS, null, null, "products", null);
  public static final DBAttribute<List<Long>> COMPONENTS = NS.linkList("components", "Components", false);
  public static final DBAttribute<List<Long>> VERSIONS = NS.linkList("versions", "Versions", false);
  public static final DBAttribute<List<Long>> MILESTONES = NS.linkList("milestones", "Milestones", false);
  public static final DBAttribute<AttributeMap> AT_DEFAULT_VALUES = NS.attributeMap("defaultValues", "Default Values");

  private static List<SingleEnumAttribute> ourUpdateDefaults = null;
  private static List<SingleEnumAttribute> getUpdateDefaults() {
    List<SingleEnumAttribute> update = ourUpdateDefaults;
    if (update == null) {
      update = Collections15.unmodifiableListCopy(SingleEnumAttribute.COMPONENT, SingleEnumAttribute.VERSION,
        SingleEnumAttribute.TARGET_MILESTONE, SingleEnumAttribute.SEVERITY, SingleEnumAttribute.PLATFORM,
        SingleEnumAttribute.PRIORITY, SingleEnumAttribute.OS, SingleEnumAttribute.ASSIGNED_TO,
        SingleEnumAttribute.STATUS);
      ourUpdateDefaults = update;
    }
    return update;
  }

  public static void updateDependencies(DBDrain drain, Task task,
    LinkedHashMap<String, BugzillaProductInformation> productInfo, Map<BugzillaAttribute, Map<String, Long>> stringMaps,
    BugzillaLists info) throws CancelledException
  {
    // todo :tr2: delete products not in productInfo? Revive removed products in productInfo?
    final PrivateMetadata pm = task.getPrivateMetadata();
    LongArray products = SingleEnumAttribute.PRODUCT.getRefLink().getReferentsView(pm).query(drain.getReader()).copyItemsSorted();
    task.checkCancelled();
    for (int i = 0; i < products.size(); i++) {
      ItemVersionCreator product = drain.changeItem(products.get(i));
      product.setAlive();
      task.checkCancelled();
      final String productName = Util.NN(ENUM_PRODUCTS.getStringId(product)).trim();
      if (productName.length() == 0) {
        Log.warn("product with no name " + product);
        continue;
      }

      List<Long> components = getDependentArray(productName, BugzillaAttribute.COMPONENT, info, stringMaps);
      components = readAllIfAbsent(components, SingleEnumAttribute.COMPONENT, drain, pm);

      List<Long> versions = getDependentArray(productName, BugzillaAttribute.VERSION, info, stringMaps);
      versions = readAllIfAbsent(versions, SingleEnumAttribute.VERSION, drain, pm);

      List<Long> milestones = getDependentArray(productName, BugzillaAttribute.TARGET_MILESTONE, info, stringMaps);
      milestones = readAllIfAbsent(milestones, SingleEnumAttribute.TARGET_MILESTONE, drain, pm);

      task.checkCancelled();
      product.setValue(COMPONENTS, components);
      product.setValue(VERSIONS, versions);
      product.setValue(MILESTONES, milestones);
      updateDefaultProductGroups(product, productName, task, productInfo);
      updateProductDefaultValues(product, productName, productInfo, stringMaps);
    }
    updateProductComponentDefaults(task, productInfo);
  }

  private static List<Long> readAllIfAbsent(List<Long> deps, SingleEnumAttribute attr, DBDrain drain, PrivateMetadata pm) {
    if(deps != null) {
      return deps;
    }
    final BoolExpr<DP> expr = BoolExpr.and(
      DPEqualsIdentified.create(DBAttribute.TYPE, attr.getType()),
      DPEqualsIdentified.create(SyncAttributes.CONNECTION, pm.thisConnection));
    return drain.getReader().query(expr).copyItemsSorted().toList();
  }

  private static void updateProductComponentDefaults(Task task,
    LinkedHashMap<String, BugzillaProductInformation> productInfo) {
    ComponentDefaultsTracker tracker = task.getContext().getComponentDefaultsTracker();
    for (Map.Entry<String, BugzillaProductInformation> e : productInfo.entrySet()) {
      String product = e.getKey();
      Map<String, ComponentDefaults> cd = e.getValue().getComponentDefaults();
      if (cd == null || cd.isEmpty())
        continue;
      tracker.report(product, cd);
    }
  }

  private static void updateProductDefaultValues(ItemVersionCreator product, String productName,
    LinkedHashMap<String, BugzillaProductInformation> productInfo, Map<BugzillaAttribute, Map<String, Long>> stringMaps) {
    BugzillaProductInformation productInformation = productInfo.get(productName);
    if (productInformation != null) {
      Map<BugzillaAttribute, String> values = productInformation.getDefaultValues();
      if (values != null) {
        AttributeMap defaultsMap = new AttributeMap();
        for (SingleEnumAttribute enumAttr : getUpdateDefaults()) {
          BugzillaAttribute bzAttr = enumAttr.getBugzillaAttribute();
          Map<String, Long> stringMap = stringMaps.get(bzAttr);
          if (stringMap == null) continue;
          String strValue = values.get(bzAttr);
          if (strValue == null) continue;
          Long reference = stringMap.get(strValue);
          defaultsMap.put(enumAttr.getBugAttribute(), reference);
        }
        product.setValue(AT_DEFAULT_VALUES, defaultsMap);
      }
    }
  }

  private static void updateDefaultProductGroups(ItemVersionCreator product, String productName, Task task,
    LinkedHashMap<String, BugzillaProductInformation> productInfo)
  {
    @Nullable Map<Long, Boolean> defaultGroups = getDefaultGroups(productName, product, task, productInfo);
    LongSet selected = new LongSet();
    if (defaultGroups != null && defaultGroups.size() > 0) {
      for (Map.Entry<Long, Boolean> entry : defaultGroups.entrySet()) {
        if (Boolean.TRUE.equals(entry.getValue())) {
          selected.add(entry.getKey());
        }
      }
      product.setSet(Group.CONTAINING_GROUPS, selected);
      product.setValue(Group.AVAILABLE_GROUPS, defaultGroups.keySet());
    }
  }

  private static Map<Long, Boolean> getDefaultGroups(String productName, DBDrain drain, Task task,
    LinkedHashMap<String, BugzillaProductInformation> productInfo)
  {
    PrivateMetadata privateMeta = task.getPrivateMetadata();

    final BugzillaProductInformation info = productInfo.get(productName);
    if (info == null)
      return null;
    List<Pair<BugGroupData, Boolean>> pairs = info.getGroups();
    if (pairs == null)
      return null;
    LinkedHashMap<Long, Boolean> result = Collections15.linkedHashMap();
    for (Pair<BugGroupData, Boolean> pair : pairs) {
      long groupItem = Group.getGroupItem(pair.getFirst(), privateMeta, drain);
      result.put(groupItem, pair.getSecond());
    }
    return result;
  }

  private static List<Long> getDependentArray(
    String productName, BugzillaAttribute attribute, BugzillaLists info,
    Map<BugzillaAttribute, Map<String, Long>> stringMaps)
  {
    Map<String, List<String>> deps = info.getProductDependencyMap(attribute);
    if(deps.isEmpty()) {
      assert BugzillaAttribute.TARGET_MILESTONE == attribute : attribute;
      return null;
    }

    List<String> values = deps.get(productName);
    if (values == null) {
      for (String key : deps.keySet()) {
        if (Util.NN(key).trim().equalsIgnoreCase(productName)) {
          values = deps.get(key);
          break;
        }
      }
    }

    List<Long> allowed = Collections15.emptyList();
    if (values != null) {
      Map<String, Long> map = stringMaps.get(attribute);
      if (map != null) {
        List<Long> allowedList = Condition.<Long>notNull().select(Convertors.fromMap(map).collectSet(values));
        if (allowedList.size() > 0) allowed = allowedList;
      }
    } else {
      if (!info.isDependenciesPresent()) {
        // set all components allowed
        Map<String, Long> all = stringMaps.get(attribute);
        if (all != null && all.size() > 0) {
          allowed = Collections15.arrayList(all.values());
        }
      }
    }
    return allowed;
  }
}
