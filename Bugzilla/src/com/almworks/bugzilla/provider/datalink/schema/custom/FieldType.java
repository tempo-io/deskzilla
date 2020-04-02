package com.almworks.bugzilla.provider.datalink.schema.custom;

import com.almworks.bugzilla.integration.data.CustomFieldType;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.RemoteSearchable;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.items.api.*;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.DBNamespace;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.text.TextUtil;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.*;

abstract class FieldType implements ItemProxy {
  static final DBNamespace NS = CustomField.NS.subNs("type");

  private static final DBIdentifiedObject O_CHOICE = NS.object("choice");
  private static final DBIdentifiedObject O_MULTI = NS.object("multi");

  static final SingleValueFieldType<?> DATE_TIME = SingleValueFieldType.date("date", CustomFieldType.DATE_TIME);

  static final FieldType UNKNOWN = new FieldType(String.class, DBAttribute.ScalarComposition.SCALAR, "unknown",
    NS.object("unknown"), CustomFieldType.UNKNOWN)
  {
    @Override
    public void updateField(ModifiableField field, List<String> options, boolean strictInfos) {
      ItemVersionCreator creator = field.getCreator();
      SyncUtils.deleteAll(creator, creator.getSlaves(CustomField.AT_OPT_FIELD));
    }

    @Override
    public boolean acceptsRawValue(@Nullable List<String> values) {
      return true;
    }

    @Override
    public void setRawValue(ItemVersionCreator bug, DBAttribute<?> attribute, @Nullable List<String> values) {
      String strValue = values == null || values.isEmpty() ? null : TextUtil.separate(values, ", ");
      //noinspection unchecked
      bug.setValue((DBAttribute<String>) attribute, strValue);
    }
  };

  static final SingleValueFieldType<?> BUG_ID =
    new SingleValueFieldType<Long>(Long.class, "bugId", CustomFieldType.BUG_ID) {
      @Override
      public boolean acceptsRawValue(@Nullable List<String> values) {
        if(values == null || values.isEmpty()) {
          return true;
        }
        if(values.size() == 1) {
          final String val = values.get(0);
          if(val == null || val.trim().isEmpty()) {
            return true;
          }
          try {
            Integer.parseInt(val);
            return true;
          } catch(NumberFormatException e) {}
        }
        return false;
      }

      @Override
      protected Long convertString(ItemVersionCreator bug, String val) {
        ItemProxy proxy = getProxy(bug, val);
        return proxy != null ? bug.materialize(proxy) : null;
      }

      private ItemProxy getProxy(ItemVersionCreator bug, String val) {
        int bugId;
        try {
          bugId = Integer.parseInt(val);
        } catch (NumberFormatException e) {
          Log.error(e);
          return null;
        }
        return Bug.createBugProxy(bug.getValue(SyncAttributes.CONNECTION), bugId);
      }
    };

  static final SingleValueFieldType<?> TEXT = SingleValueFieldType.text("shortText", CustomFieldType.TEXT);
  static final SingleValueFieldType<?> LONG_TEXT = SingleValueFieldType.text("longText", CustomFieldType.LARGE_TEXT);

  private static final List<? extends FieldType> SINGLE_VALUE =
    Collections15.unmodifiableListCopy(DATE_TIME, UNKNOWN, BUG_ID, TEXT, LONG_TEXT);

  static final Map<DBIdentifiedObject, FieldUploader> UPLOADERS;
  static {
    HashMap<DBIdentifiedObject,FieldUploader> uploaders = Collections15.hashMap();
    uploaders.put(O_CHOICE, FieldUploader.CF_SINGLE_ENUM);
    uploaders.put(O_MULTI, FieldUploader.CF_MULTI_ENUM);
    uploaders.put(DATE_TIME.getKind(), FieldUploader.CF_DATE);
    uploaders.put(BUG_ID.getKind(), FieldUploader.CF_BUG_ID);
    uploaders.put(TEXT.getKind(), FieldUploader.CF_TEXT);
    uploaders.put(LONG_TEXT.getKind(), FieldUploader.CF_TEXT);
    uploaders.put(UNKNOWN.getKind(), FieldUploader.CF_UNKNOWN);
    UPLOADERS = Collections.unmodifiableMap(uploaders);
  }

  static final Map<DBIdentifiedObject, AutoMergePolicy> MERGES;
  static {
    HashMap<DBIdentifiedObject, AutoMergePolicy> m = Collections15.hashMap();
    m.put(O_CHOICE, AutoMergePolicy.DO_NOTHING);
    m.put(O_MULTI, AutoMergePolicy.MERGE_ITEM_SET);
    m.put(DATE_TIME.getKind(), AutoMergePolicy.DO_NOTHING);
    m.put(BUG_ID.getKind(), AutoMergePolicy.DO_NOTHING);
    m.put(TEXT.getKind(), AutoMergePolicy.TEXT);
    m.put(LONG_TEXT.getKind(), AutoMergePolicy.TEXT);
    m.put(UNKNOWN.getKind(), AutoMergePolicy.DO_NOTHING);
    MERGES = Collections.unmodifiableMap(m);
  }

  static final Map<DBIdentifiedObject, RemoteSearchable> REMOTE_SEARCH;
  static {
    HashMap<DBIdentifiedObject, RemoteSearchable> m = Collections15.hashMap();
    m.put(O_CHOICE, CfRemoteSearch.SINGLE_ENUM);
    m.put(O_MULTI, CfRemoteSearch.MULTI_ENUM);
    m.put(DATE_TIME.getKind(), CfRemoteSearch.DATE);
    m.put(BUG_ID.getKind(), CfRemoteSearch.TEXT_CONSTRAINT);
    m.put(TEXT.getKind(), CfRemoteSearch.TEXT_CONSTRAINT);
    m.put(LONG_TEXT.getKind(), CfRemoteSearch.TEXT_CONSTRAINT);
//    m.put(UNKNOWN.getKind(), );
    REMOTE_SEARCH = Collections.unmodifiableMap(m);
  }

  static final Map<DBIdentifiedObject, FieldFactory> FIELD_FACTORIES;
  static {
    HashMap<DBIdentifiedObject, FieldFactory> m = Collections15.hashMap();
    m.put(O_CHOICE, FieldFactory.SINGLE_ENUM);
    m.put(O_MULTI, FieldFactory.MULTI_ENUM);
    m.put(DATE_TIME.getKind(), FieldFactory.DATE);
    m.put(BUG_ID.getKind(), FieldFactory.BUG_ID);
    m.put(TEXT.getKind(), FieldFactory.TEXT);
    m.put(LONG_TEXT.getKind(), FieldFactory.LARGE_TEXT);
    m.put(UNKNOWN.getKind(), FieldFactory.NONE);
    FIELD_FACTORIES = Collections.unmodifiableMap(m);
  }

  private static final Map<DBIdentifiedObject, CustomFieldType> FIELD_TYPES;
  static {
    HashMap<DBIdentifiedObject, CustomFieldType> m = Collections15.hashMap();
    m.put(DATE_TIME.getKind(), CustomFieldType.DATE_TIME);
    m.put(BUG_ID.getKind(), CustomFieldType.BUG_ID);
    m.put(TEXT.getKind(), CustomFieldType.TEXT);
    m.put(LONG_TEXT.getKind(), CustomFieldType.LARGE_TEXT);
    m.put(UNKNOWN.getKind(), CustomFieldType.UNKNOWN);
    m.put(O_CHOICE, CustomFieldType.CHOICE);
    m.put(O_MULTI, CustomFieldType.MULTI_SELECT);
    FIELD_TYPES = Collections.unmodifiableMap(m);
  }

  private final Class<?> myScalarClass;
  private final DBAttribute.ScalarComposition myComposition;
  private final String myTypeId;
  private final DBIdentifiedObject myKind;
  private final CustomFieldType myType;

  FieldType(Class<?> scalarClass, DBAttribute.ScalarComposition composition, String typeId,
    DBIdentifiedObject kind, CustomFieldType type) {
    myScalarClass = scalarClass;
    myComposition = composition;
    myTypeId = typeId;
    myKind = kind;
    myType = type;
  }

  public static FieldType fromExternal(CustomFieldType type, ModifiableField field) {
    switch (type) {
    case CHOICE: return new EnumFieldType(DBAttribute.ScalarComposition.SCALAR, "choice", O_CHOICE, field, type);
    // todo :dyoma: LIST or SET? decide consistently with MultiSelect implementation
    case MULTI_SELECT: return new EnumFieldType(DBAttribute.ScalarComposition.LIST, "multi", O_MULTI, field, type);
    }
    for (FieldType fieldType : SINGLE_VALUE) if (type == fieldType.getType()) return fieldType;
    Log.warn("Unknown type: " + type);
    return UNKNOWN;
  }

  public DBAttribute<?> createAttribute(PrivateMetadata pm, String id) {
    String attrId = pm.namespace().subNs("cf").attr(myTypeId + id);
    return DBAttribute.create(attrId, id, myScalarClass, myComposition, false);
  }

  @Override
  public long findItem(DBReader reader) {
    return reader.findMaterialized(myKind);
  }

  @Override
  public long findOrCreate(DBDrain drain) {
    return drain.materialize(myKind);
  }

  public abstract void updateField(ModifiableField field, List<String> options, boolean strictInfos);

  public CustomFieldType getType() {
    return myType;
  }

  public String getTypeId() {
    return myTypeId;
  }

  public static FieldType read(ModifiableField field) {
    CustomFieldType type = field.getCreator().mapValue(CustomField.AT_TYPE, FIELD_TYPES);
    if (type != null) return fromExternal(type, field);
    Log.error("Missing field type " + field);
    return UNKNOWN;
  }

  public DBIdentifiedObject getKind() {
    return myKind;
  }

  protected DBAttribute.ScalarComposition getComposition() {
    return myComposition;
  }

  public abstract boolean acceptsRawValue(@Nullable List<String> values);

  public abstract void setRawValue(ItemVersionCreator bug, DBAttribute<?> attribute, @Nullable List<String> values);
}
