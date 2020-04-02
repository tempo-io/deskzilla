package com.almworks.bugzilla.provider.datalink.schema.custom;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ResolvedItem;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.custom.BugzillaCustomField;
import com.almworks.bugzilla.provider.custom.VisibilityConstraint;
import com.almworks.bugzilla.provider.datalink.DataLink;
import com.almworks.bugzilla.provider.datalink.RemoteSearchable;
import com.almworks.bugzilla.provider.datalink.schema.EnumType;
import com.almworks.engine.items.DatabaseUnwrapper;
import com.almworks.explorer.qbuilder.filter.*;
import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.dp.*;
import com.almworks.items.sync.*;
import com.almworks.items.sync.impl.AttributeInfo;
import com.almworks.items.sync.util.*;
import com.almworks.items.util.DBNamespace;
import com.almworks.items.util.SyncAttributes;
import com.almworks.itemsync.MergeOperationsManager;
import com.almworks.util.Pair;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.LongSet;
import com.almworks.util.collections.MultiMap;
import com.almworks.util.properties.TypedKeyWithEquality;
import org.almworks.util.*;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.*;

import static java.util.Collections.singletonList;
import static org.almworks.util.Collections15.arrayList;

public class CustomField {
  private static final TypedKey<Pair<Long, LongList>> ALL_FIELDS_NO_REMOVED = TypedKey.create("allFields");
  private static final TypedKey<Pair<Long, LongList>> ALL_FIELDS_INCL_REMOVED = TypedKey.create("allFieldsIncludingRemoved");

  static final DBNamespace NS = BugzillaProvider.NS.subModule("cf");

  private static final DBNamespace FLD_NS = NS.subNs("field");
  // Should be private since it is used for caches
  private static final DBItemType typeField = FLD_NS.type();
  public static final DBAttribute<String> AT_ID = FLD_NS.string("id");
  public static final DBAttribute<String> AT_NAME = FLD_NS.string("name");
  public static final DBAttribute<Integer> AT_ORDER = FLD_NS.integer("order");
  public static final DBAttribute<Long> AT_TYPE = FLD_NS.link("fieldType");
  public static final DBAttribute<Boolean> AT_AVAILABLE_ON_SUBMIT = FLD_NS.bool("availableOnSubmit");
  static final DBAttribute<Long> AT_ATTRIBUTE = FLD_NS.master("attribute");
  public static final DBAttribute<List<Long>> AT_VIS_OPTIONS = FLD_NS.linkList("visibilityOptions", "Visibility Options List", false);
  public static final DBAttribute<Long> AT_OPT_VIS_FIELD = FLD_NS.link("optionVisibilityField");

  private static final DBNamespace OPT_NS = NS.subNs("option");
  public static final DBItemType typeOption = OPT_NS.type();
  public static final DBAttribute<Long> AT_OPT_FIELD = OPT_NS.master("field");
  public static final DBAttribute<String> AT_OPT_VALUE = OPT_NS.string("id");
  public static final DBAttribute<Integer> AT_OPT_ORDER = OPT_NS.integer("order");
  public static final DBAttribute<Long> AT_OPT_VIS_VALUE = OPT_NS.link("optionVisibilityValue");

  static final DBAttribute<Long> AT_ATTR_FIELD = NS.subNs("attribute").link("field");

  /**
   * Auto merger for bug.
   * */
  public static final ItemAutoMerge AUTO_MERGE = new SimpleAutoMerge() {
    @Override
    public void resolve(AutoMergeData data) {
      VersionSource db = data.getLocal().getElderVersion();
      for (DBAttribute<?> attribute : data.getUnresolved()) {
        ItemVersion field = getField(db.getReader(), attribute);
        if (field == null) continue;
        AutoMergePolicy<?> policy = field.mapValue(AT_TYPE, FieldType.MERGES);
        if (policy == null) Log.error("Missing merge policy for " + attribute);
        else //noinspection unchecked
          policy.resolve(data, (DBAttribute)attribute);
      }

    }
  };
  public static final DataLink DATA_LINK = new BugzillaCustomFieldsLink();

  public static void registerCustomFieldMergers(MergeOperationsManager mm) {
    // We need some automerge operation for items representing custom fields, otherwise they will not be removed.
    mm.addMergeOperation(new DiscardAll(), typeField);
  }

  public static void syncFields(DBDrain drain, PrivateMetadata pm, boolean strictInfos,
    Map<String, CustomFieldInfo> infos, MultiMap<String, String> defaults, @Nullable Map<String, String> allNames,
    CustomFieldDependencies depMap)
  {
    SyncFields sync = new SyncFields(drain, pm);
    sync.loadAllFields(true);
    sync.createOrUpdate(infos, strictInfos);
    if (allNames != null) sync.updateNames(allNames);
    if (strictInfos) sync.removeMissings();
    sync.setValues(drain.changeItem(pm.bugPrototype), infos.keySet(), defaults);
    sync.updateDependencies(depMap);
  }

  public static void updateFieldDependencies(DBDrain drain, PrivateMetadata pm, Map<String, CustomFieldInfo> cfi,
    CustomFieldDependencies cfd)
  {
    SyncFields sync = new SyncFields(drain, pm);
    sync.loadAllFields(false);
    sync.createOrUpdate(cfi, false);
    sync.updateDependencies(cfd);
  }

  public static boolean isCustomFieldAttribute(ItemVersion attribute) {
    if (attribute == null) return false;
    Long field = attribute.getValue(AT_ATTR_FIELD);
    return field != null && field > 0;
  }

  public static long setFieldValue(String id, @Nullable CustomFieldInfo info, ItemVersionCreator bug,
    PrivateMetadata pm, @Nullable List<String> valueList)
  {
    SyncFields sync = new SyncFields(bug, pm);
    sync.loadAllFields(false);
    ModifiableField field = sync.createOrUpdateField(id, info, false);
    field.setValue(bug, valueList);
    return field.getItem();
  }

  public static void clearOtherFields(ItemVersionCreator bug, PrivateMetadata pm, LongList setFields) {
    if (setFields == null) setFields = LongList.EMPTY;
    SyncFields sync = new SyncFields(bug, pm);
    sync.loadAllFields(false);
    for (ModifiableField field : sync.getFields())
      if (!setFields.contains(field.getItem())) field.setValue(bug, null);
  }

  public static List<DBAttribute<?>> selectCustomFields(VersionSource source, Collection<? extends DBAttribute<?>> attributes) {
    List<DBAttribute<?>> result = Collections15.arrayList();
    for (DBAttribute<?> attribute : attributes)  if (isCustomFieldAttribute(source.forItem(attribute))) result.add(attribute);
    return result.isEmpty() ? Collections.<DBAttribute<?>>emptyList() : result;
  }

  public static void buildUploadInfo(BugInfoForUpload info, ItemVersion bug, DBAttribute<?> attribute) {
    ItemVersion field = getField(bug.getReader(), attribute);
    if (field == null) return;
    FieldUploader uploader = field.mapValue(AT_TYPE, FieldType.UPLOADERS);
    if (uploader == null) {
      Log.error("unknown type for field " + field.getItem() + " " + field.getValue(AT_TYPE));
      return;
    }
    assert bug.findMaterialized(attribute) == field.getValue(AT_ATTRIBUTE) : field.getItem();
    //noinspection unchecked
    uploader.buildUploadInfo(info, bug, attribute);
  }

  @Nullable
  public static String getFieldId(DBReader reader, DBAttribute<?> attribute) {
    ItemVersion field = getField(reader, attribute);
    return field != null ? field.getValue(AT_ID) : null;
  }

  public static String getOptionName(ItemVersion option) {
    return option.getValue(AT_OPT_VALUE);
  }

  @Nullable
  public static RemoteSearchable getRemoteSearch(DBReader reader, DBAttribute attribute) {
    ItemVersion field = getField(reader, attribute);
    if (field == null) return null;
    RemoteSearchable search = field.mapValue(AT_TYPE, FieldType.REMOTE_SEARCH);
    if (search == null) Log.error("Missing remote search for " + attribute);
    return search;
  }

  @Nullable
  private static ItemVersion getField(DBReader reader, DBAttribute<?> attribute) {
    if (attribute == null) return null;
    ItemVersion attr = SyncUtils.readTrunk(reader, attribute);
    Long fieldItem = attr.getValue(AT_ATTR_FIELD);
    if (fieldItem == null || fieldItem <= 0) return null;
    return SyncUtils.readTrunk(reader, fieldItem);
  }

  public static BoolExpr<DP> getCFAttributeFilter(PrivateMetadata pm) {
    return BoolExpr.and(DPNotNull.create(AT_ATTR_FIELD),
      DPEqualsIdentified.create(SyncAttributes.CONNECTION, pm.thisConnection));
  }

  public static BugzillaCustomField createCustomField(ItemVersion attr, BugzillaCustomFields fields,
    @Nullable BugzillaCustomField prevField) {
    Long fieldItem = attr.getValue(AT_ATTR_FIELD);
    if (fieldItem == null || fieldItem < 0) {
      Log.error("Not a custom field attribute " + attr);
      return null;
    }
    ItemVersion field = attr.forItem(fieldItem);
    if (SyncUtils.isRemoved(field)) return null;
    String id =  field.getValue(AT_ID);
    if (id == null) {
      assert false : field;
      Log.warn("cannot use custom field " + field);
      return null;
    }
    FieldFactory factory = field.mapValue(AT_TYPE, FieldType.FIELD_FACTORIES);
    if (factory == null) {
      Log.error("Missing factory for " + field);
      return null;
    }
    @Nullable String displayName = field.getValue(AT_NAME);
    @Nullable Boolean availableOnSubmit = field.getValue(AT_AVAILABLE_ON_SUBMIT);

    int order = field.getNNValue(AT_ORDER, Integer.MAX_VALUE);

    return factory.createField(attr, id, displayName, availableOnSubmit, order, fields, prevField);
  }

  @Nullable
  public static VisibilityConstraint getFieldVisibility(DBReader reader, DBAttribute<?> fieldAttribute) {
    ItemVersion field = getField(reader, fieldAttribute);
    if (field == null) return null;
    List<Long> visOptions = field.getValue(AT_VIS_OPTIONS);
    if (visOptions == null) return null;
    List<VisibilityControllingOption> vcOptions = arrayList(visOptions.size());
    for (Long visOption : visOptions) vcOptions.add(getVisibility(field, visOption));
    return VisibilityControllingOption.collect(vcOptions);
  }

  @Nullable
  public static VisibilityConstraint getOptionVisibilityParameters(ItemVersion option) {
    return VisibilityControllingOption.singleOption(getVisibility(option, option.getValue(AT_OPT_VIS_VALUE)));
  }

  private static VisibilityControllingOption getVisibility(ItemVersion item, Long visOptionItem) {
    if (visOptionItem == null || visOptionItem <= 0) return null;
    ItemVersion visOption = item.forItem(visOptionItem);

    String controlFieldId;
    DBAttribute<?> visAttr;
    String controlValue = visOption.getValue(AT_OPT_VALUE);
    if (controlValue == null) {
      // standard Bugzilla field?
      Long typeItem = visOption.getValue(DBAttribute.TYPE);
      ItemVersion type =   typeItem == null ? null :      item.forItem(typeItem);
      Long bugAttrItem =   type == null ? null :          type.getValue(EnumType.BUG_ATTR);
      Long idAttrItem =    type == null ? null :          type.getValue(EnumType.ID_ATTR);
      visAttr =            bugAttrItem == null ? null :   AttributeInfo.getAttribute(item, bugAttrItem);
      DBAttribute idAttr = idAttrItem == null ? null :    AttributeInfo.getAttribute(item, idAttrItem);
      if (idAttr == null || idAttr.getScalarClass() != String.class || idAttr.getComposition() != DBAttribute.ScalarComposition.SCALAR) {
        Log.warn("CF-visi:option attr " + idAttr + " " + (idAttr != null ? idAttr.getScalarClass() : ""));
        assert false;
        return null;
      }
      controlValue =       idAttr == null ? null :        (String)visOption.getValue(idAttr);
      controlFieldId =     visAttr == null ? null :       visAttr.getName();
      if (controlFieldId == null || controlValue == null || visAttr == null) {
        Log.warn("CF-visi:opt" + visOption.getAllValues());
        return null;
      }
    } else {
      // custom field
      Long visFieldItem = visOption.getValue(AT_OPT_FIELD);
      if (visFieldItem == null || visFieldItem <= 0) {
        Log.warn("broken visibility option " + visOptionItem);
        return null;
      }
      ItemVersion visField = item.forItem(visFieldItem);
      controlFieldId = visField.getValue(AT_ID);
      Long visAttrId = visField.getValue(AT_ATTRIBUTE);
      if (visAttrId == null || visAttrId <= 0) {
        Log.warn("broken field " + visFieldItem);
        return null;
      }
      visAttr = AttributeInfo.getAttribute(item, visAttrId);
    }
    return new VisibilityControllingOption(controlFieldId, visAttr, controlValue, visOptionItem);
  }

  @Nullable
  public static LoadedField loadOptionVisibilityField(DBReader reader, DBAttribute<?> attribute) {
    ItemVersion field = getField(reader, attribute);
    Long optVisFieldItem = field == null ? null : field.getValue(AT_OPT_VIS_FIELD);
    ItemVersion optVisField = optVisFieldItem == null ? null : field.forItem(optVisFieldItem);
    if (optVisField == null) return null;
    Long optVisAttr = optVisField.getValue(AT_ATTRIBUTE);
    if (optVisAttr != null) {
      // visilibility controller is a custom field
      return LoadedField.create(optVisField.getValue(AT_ID), optVisField.getValue(AT_NAME), optVisField.getItem(), AttributeInfo.getAttribute(optVisField, optVisAttr), optVisAttr);
    } else {
      // visibility controller is a standard Bugzilla field
      Long bugAttrItem = optVisField.getValue(EnumType.BUG_ATTR);
        if (bugAttrItem == null) return null;
      String bugzillaId = field.forItem(bugAttrItem).getValue(DBAttribute.NAME);
      return LoadedField.create(bugzillaId, bugzillaId, optVisFieldItem, AttributeInfo.getAttribute(field, bugAttrItem), bugAttrItem);
    }
  }

  public static BaseEnumConstraintDescriptor createDescriptor(Lifespan life, DBAttribute<?> attribute, String displayName,
    BoolExpr<DP> optionsFilter, CommonMetadata md)
  {
    assert attribute.getScalarClass() == Long.class;
    boolean singular = attribute.getComposition() == DBAttribute.ScalarComposition.SCALAR;
    EnumConstraintKind kind = singular ? EnumConstraintKind.INCLUSION : EnumConstraintKind.INTERSECTION;
    EnumNarrower narrower = new EnumNarrower.ConnectionFilteringNarrower() {
        @Override
        protected boolean isAccepted(ResolvedItem item, @NotNull Collection<Long> connections, ItemHypercube cube) {
          if (!super.isAccepted(item, connections, cube)) return false;
          if (!(item instanceof BugzillaCustomField.ResolvedCustomFieldOption)) return true;
          VisibilityConstraint c = ((BugzillaCustomField.ResolvedCustomFieldOption) item).getVisibilityConstraint();
          return c == null || c.isAllowed(cube);
        }
      };
    TypedKeyWithEquality<ItemKeyModelCollector<BugzillaCustomField.ResolvedCustomFieldOption>> collectorKey =
      TypedKeyWithEquality.create(attribute);
    ItemKeyModelCollector<?> modelCollector = EnumType.createKeyCollector(md, optionsFilter, collectorKey,
      BugzillaCustomField.ResolvedCustomFieldOption.FACTORY, "cf-" + attribute.getName());
    BaseEnumConstraintDescriptor descriptor =
      BaseEnumConstraintDescriptor.createAndFetchNotSet(attribute, narrower, displayName, null, kind, null, null,
        ItemKey.COMPARATOR, null, false, modelCollector);
    modelCollector.start(life, md.getDatabase());
    return descriptor;
  }

  public static BoolExpr<DP> getOptionFilter(DBAttribute<?> attr, VersionSource source) {
    assert attr.getScalarClass() == Long.class : attr.getScalarClass();
    ItemVersion attribute = source.forItem(attr);
    Long field = attribute.getValue(AT_ATTR_FIELD);
    if (field == null) return null;
    return DPEquals.create(AT_OPT_FIELD, field);
  }

  static ItemVersionCreator basicCreateField(DBDrain drain, PrivateMetadata pm, String id) {
    ItemVersionCreator field = drain.createItem();
    field.setValue(DBAttribute.TYPE, typeField);
    field.setValue(SyncAttributes.CONNECTION, pm.getConnectionRef());
    field.setValue(AT_ID, id);
    //noinspection unchecked
    drain.getReader().getTransactionCache().put(ALL_FIELDS_NO_REMOVED, null);
    drain.getReader().getTransactionCache().put(ALL_FIELDS_INCL_REMOVED, null);
    return field;
  }

  public static BoolExpr<DP> createConnectionCustomFieldsExpr(PrivateMetadata pm) {
    return
      DPEqualsIdentified.create(SyncAttributes.CONNECTION, pm.getConnectionRef())
      .and(DPEqualsIdentified.create(DBAttribute.TYPE, typeField));
  }

  private static class SyncFields {
    private final DBDrain myDrain;
    private final PrivateMetadata myPm;
    private final Map<String, ModifiableField> myFields = Collections15.hashMap();
    private final LongSet myToRemove = new LongSet();

    private SyncFields(DBDrain drain, PrivateMetadata pm) {
      myDrain = drain;
      myPm = pm;
    }

    public void loadAllFields(boolean includeRemoved) {
      LongList fields = queryAllFields(includeRemoved);
      for (ItemVersionCreator f : myDrain.changeItems(fields)) {
        ModifiableField field = ModifiableField.load(f, myPm);
        String id = field.getId();
        if (id != null) {
          myFields.put(id, field);
          myToRemove.add(f.getItem());
        } else {
          Log.error("Missing field Id");
          f.delete();
        }
      }
    }

    private LongList queryAllFields(boolean includeRemoved) {
      TypedKey<Pair<Long, LongList>> cachedFieldsKey = includeRemoved ? ALL_FIELDS_INCL_REMOVED : ALL_FIELDS_NO_REMOVED;
      Map map = myDrain.getReader().getTransactionCache();
      @SuppressWarnings({"unchecked"})
      Pair<Long, LongList> cached = (Pair<Long, LongList>) map.get(cachedFieldsKey);
      long connection = myDrain.materialize(myPm.getConnectionRef());
      if (cached != null) {
        if (connection == cached.getFirst()) return cached.getSecond();
      }
      BoolExpr<DP> expr = createConnectionCustomFieldsExpr(myPm);
      DBQuery query = includeRemoved ? DatabaseUnwrapper.query(myDrain.getReader(), expr) : myDrain.getReader().query(expr);
      LongList fields = query.copyItemsSorted();
      //noinspection unchecked
      map.put(cachedFieldsKey, Pair.create(connection, fields));
      return fields;
    }

    public Collection<ModifiableField> getFields() {
      return myFields.values();
    }

    public void createOrUpdate(Map<String, CustomFieldInfo> infos, boolean strictInfos) {
      for (Map.Entry<String, CustomFieldInfo> entry : infos.entrySet()) {
        String id = entry.getKey();
        CustomFieldInfo info = entry.getValue();
        createOrUpdateField(id, info, strictInfos);
      }
    }

    private ModifiableField createOrUpdateField(String id, @Nullable CustomFieldInfo info, boolean strictInfos) {
      ModifiableField field = getExistingField(id);
      if (field == null) {
        field = createField(id, info != null ? info.getType() : null);
      }
      if (info != null) field.update(info, strictInfos);
      myToRemove.remove(field.getItem());
      return field;
    }

    private ModifiableField createField(String id, @Nullable CustomFieldType externalType) {
      ModifiableField field = ModifiableField.createNew(myDrain, myPm, id, externalType);
      myFields.put(id, field);
      return field;
    }

    public void removeMissings() {
      SyncUtils.deleteAll(myDrain, myToRemove);
      myToRemove.clear();
    }

    public void updateNames(Map<String, String> allNames) {
      for (Map.Entry<String, String> entry : allNames.entrySet()) {
        String id = entry.getKey();
        String name = entry.getValue();
        ModifiableField field = createOrUpdateField(id, null, false);
        field.updateName(name);
      }
    }

    public void setValues(ItemVersionCreator bug, Set<String> fieldIds, MultiMap<String, String> values) {
      for (String id : fieldIds) {
        ModifiableField field = getExistingField(id);
        if (field == null) continue;
        field.setValue(bug, values.getAll(id));
      }
    }

    private ModifiableField getExistingField(String id) {
      return myFields.get(id);
    }

    public void updateDependencies(CustomFieldDependencies depMap) {
      for (ModifiableField field : getFields()) {
        CustomFieldDependencies.Dependency dep = depMap.getDependency(field.getId());
        if(field.trusts(dep, depMap.getSource())) {
          field.updateFieldVisibility(dep, getExistingField(dep.getVisibilityControllerField()));
          field.updateOptionsVisibility(dep, getExistingField(dep.getValuesControllerField()));
        }
      }
    }
  }
  
  private static class VisibilityControllingOption {
    private final String myControllerFieldId;
    private final DBAttribute myControllerAttr;
    private final String myControllerValueId;
    private final long myControllerValueItem;

    private VisibilityControllingOption(String controllerFieldId, DBAttribute controllerAttr, String controllerValueId, long controllerValueItem) {
      myControllerFieldId = controllerFieldId;
      myControllerAttr = controllerAttr;
      myControllerValueId = controllerValueId;
      myControllerValueItem = controllerValueItem;
    }
    
    public static VisibilityConstraint singleOption(@Nullable VisibilityControllingOption option) {
      return option == null 
        ? null 
        : new VisibilityConstraint(option.myControllerFieldId, option.myControllerAttr, singletonList(option.myControllerValueId), LongArray.singleton(
        option.myControllerValueItem));
    }
    
    public static VisibilityConstraint collect(@NotNull Collection<VisibilityControllingOption> options) {
      String commonControllerFieldId = null;
      DBAttribute commonControllerAttr = null;
      List<String> controllerValuesIds = arrayList(options.size());
      LongArray controllerValuesItems = new LongArray(options.size());
      for (VisibilityControllingOption option : options) {
        if (option == null) continue;
        if (commonControllerFieldId == null) commonControllerFieldId = option.myControllerFieldId;
        else if (!commonControllerFieldId.equals(option.myControllerFieldId)) {
          Log.error("Visibility controlling option: different controller fields " + commonControllerFieldId + " " + option.myControllerFieldId);
          continue;
        }
        if (commonControllerAttr == null) commonControllerAttr = option.myControllerAttr;
        else if (!commonControllerAttr.equals(option.myControllerAttr)) {
          Log.error("Visibility controlling option: different controller attrs " + commonControllerAttr + " " + option.myControllerAttr);
          continue;
        }
        if (option.myControllerValueId != null) {
          controllerValuesIds.add(option.myControllerValueId);
          controllerValuesItems.add(option.myControllerValueItem);
        }
      }
      return controllerValuesIds.isEmpty() ? null : new VisibilityConstraint(commonControllerFieldId, commonControllerAttr, controllerValuesIds, controllerValuesItems);
    }
  }
}
