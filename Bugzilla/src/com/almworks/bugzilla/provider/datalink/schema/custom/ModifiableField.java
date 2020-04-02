package com.almworks.bugzilla.provider.datalink.schema.custom;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.BugzillaHTMLConstants;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.BugzillaAttributeLink;
import com.almworks.bugzilla.provider.datalink.ReferenceLink;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.DBDrain;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.items.sync.impl.AttributeInfo;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.Pair;
import com.almworks.util.commons.Function;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.Map;

import static org.almworks.util.Collections15.arrayList;

class ModifiableField {
  private final PrivateMetadata myPM;
  private final ItemVersionCreator myCreator;
  private FieldType myType = null;
  private DBAttribute<?> myAttribute = null;
  private String myId = null;
  private String myName = null;
  private Integer myOrder = null;

  ModifiableField(ItemVersionCreator item, PrivateMetadata pm) {
    myCreator = item;
    myPM = pm;
  }

  public static ModifiableField load(ItemVersionCreator creator, PrivateMetadata pm) {
    return new ModifiableField(creator, pm);
  }

  public String getId() {
    if (myId == null) myId = myCreator.getValue(CustomField.AT_ID);
    return myId;
  }

  public static ModifiableField createNew(DBDrain drain, PrivateMetadata pm, String id, CustomFieldType externalType) {
    ItemVersionCreator field = CustomField.basicCreateField(drain, pm, id);
    ModifiableField loaded = load(field, pm);
    if (externalType == null) externalType = CustomFieldType.UNKNOWN;
    loaded.setType(externalType);
    return loaded;
  }

  public long getItem() {
    return myCreator.getItem();
  }

  ItemVersionCreator getCreator() {
    return myCreator;
  }

  PrivateMetadata getPM() {
    return myPM;
  }

  public void update(CustomFieldInfo info, boolean strictInfos) {
    myCreator.setAlive();
    updateName(info.getDisplayName());
    updateOrder(info);
    Boolean submit = info.getAvailableOnSubmit();
    if (submit != null) myCreator.setValue(CustomField.AT_AVAILABLE_ON_SUBMIT, submit);
    FieldType type = getType();
    if (type.getType() != info.getType()) setType(info.getType());
    myType.updateField(this, info.getOptions(), strictInfos);
  }

  void setType(CustomFieldType externalType) {
    if (externalType == null) {
      Log.error("Illegal external type <null>");
      return;
    }
    FieldType type = FieldType.fromExternal(externalType, this);
    myType = type;
    myCreator.setValue(CustomField.AT_TYPE, type);
    updateAttribute();
  }

  private Integer getOrder() {
    if (myOrder == null) myOrder = myCreator.getValue(CustomField.AT_ORDER);
    return myOrder;
  }

  void updateName(String displayName) {
    if (displayName == null || displayName.trim().length() == 0) return;
    if (Util.equals(myName, displayName)) return;
    myCreator.setValue(CustomField.AT_NAME, displayName);
    myName = displayName;
  }

  private void updateOrder(CustomFieldInfo info) {
    Integer oldOrder = getOrder();
    int newOrder = info.getOrder();
    if (oldOrder == null || oldOrder < newOrder) {
      myCreator.setValue(CustomField.AT_ORDER, newOrder);
      myOrder = newOrder;
    }
  }

  public String getName() {
    if (myName == null) myName = myCreator.getValue(CustomField.AT_NAME);
    return myName;
  }

  public FieldType getType() {
    if (myType == null) myType = FieldType.read(this);
    return myType;
  }

  public DBAttribute<?> getAttribute() {
    FieldType type = getType();
    if (myAttribute == null && type != null) {
      Long attr = myCreator.getValue(CustomField.AT_ATTRIBUTE);
      if (attr != null) myAttribute = AttributeInfo.getAttribute(myCreator, attr);
      else updateAttribute();
    }
    return myAttribute;
  }

  private void updateAttribute() {
    Long oldAttribute = myCreator.getValue(CustomField.AT_ATTRIBUTE);
    myAttribute = getType().createAttribute(myPM, getId());
    myCreator.setValue(CustomField.AT_ATTRIBUTE, myAttribute);
    ItemVersionCreator newAttribute = myCreator.changeItem(myAttribute);
    newAttribute.setValue(CustomField.AT_ATTR_FIELD, getItem());
    newAttribute.setValue(SyncAttributes.SHADOWABLE, true);
    newAttribute.setValue(SyncAttributes.CONNECTION, myCreator.getValue(SyncAttributes.CONNECTION));
    if (oldAttribute != null && oldAttribute > 0 && oldAttribute != newAttribute.getItem())
      myCreator.changeItem(oldAttribute).setValue(CustomField.AT_ATTR_FIELD, (Long)null);
  }

  public void setValue(ItemVersionCreator bug, @Nullable List<String> values) {
    DBAttribute<?> attribute = getAttribute();
    if (attribute == null) return;
    if (values == null || values.isEmpty()) {
      bug.setValue(attribute, null);
      return;
    }
    FieldType type = getType();
    if (type == null) return;
    if (type.acceptsRawValue(values)) {
      type.setRawValue(bug, attribute, values);
    } else {
      assert type != FieldType.UNKNOWN;
      Log.warn("Values " + String.valueOf(values)
        + " for field " + getId()
        + " are not accepted by CF type " + myType.getTypeId()
        + "; converting CF to type UNKNOWN");
      setType(CustomFieldType.UNKNOWN);
      final FieldType newType = getType();
      assert newType == FieldType.UNKNOWN;
      newType.updateField(this, null, false);
      newType.setRawValue(bug, getAttribute(), values);
    }
  }

  @Nullable
  public EnumFieldType getTypeAsEnum() {
    FieldType type = getType();
    return Util.castNullable(EnumFieldType.class, type);
  }

  void updateFieldVisibility(@NotNull CustomFieldDependencies.Dependency dep, @Nullable ModifiableField controlCustomField) {
    myCreator.setValue(CustomField.AT_VIS_OPTIONS, getFieldVisibilityOptionItems(dep, controlCustomField));
  }

  /** @return null or non-empty list */
  @Nullable
  private List<Long> getFieldVisibilityOptionItems(CustomFieldDependencies.Dependency dep, ModifiableField controlCustomField) {
    Function<String, Long> controllerOptionsResolver;
    if (controlCustomField != null) {
      controllerOptionsResolver = getCustomEnumVisOptCreator(controlCustomField);
    } else {
      // it may be a standard Bugzilla field
      controllerOptionsResolver = getStandardFieldVisOptCreator(dep.getVisibilityControllerField());
    }
    if (controllerOptionsResolver == null) return null;
    
    List<String> controlValueIds = dep.getVisibilityControllerValues();
    if (controlValueIds == null) return null;
    List<Long> controlValues = arrayList(controlValueIds.size());
    for (String controlValueId : controlValueIds) {
      Long controlValue = controllerOptionsResolver.invoke(controlValueId);
      if (controlValue != null && controlValue > 0L) controlValues.add(controlValue);
    }
    return controlValues.isEmpty() ? null : controlValues;
  }
  
  @Nullable
  private static Function<String, Long> getCustomEnumVisOptCreator(@NotNull ModifiableField controlCustomField) {
    final EnumFieldType enumType = controlCustomField.getTypeAsEnum();
    if (enumType == null) { 
      Log.error("Visibility controller custom field must be enum " + controlCustomField);
      return null;
    } else {
      return new Function<String, Long>() { @Override public Long invoke(String controlValueId) {
        return enumType.findOrCreateOption(controlValueId);
      }}; 
    }    
  } 
  
  @Nullable
  private Function<String, Long> getStandardFieldVisOptCreator(String controllerFieldId) {
    BugzillaAttribute controllerAttr = BugzillaHTMLConstants.STANDARD_FIELD_IDS_IN_DEPENDENCIES_MAP.get(controllerFieldId);
    BugzillaAttributeLink controlLink = CommonMetadata.ATTR_TO_LINK.get(controllerAttr);
    if (controlLink instanceof ReferenceLink) {
      final ReferenceLink refLink = (ReferenceLink) controlLink;
      return new Function<String, Long>() { @Override public Long invoke(String controlValueId) {
        return refLink.getOrCreateReferent(myPM, controlValueId, myCreator);
      }};
    } 
    return null;
  }
  

  void updateOptionsVisibility(@NotNull CustomFieldDependencies.Dependency dep, @Nullable ModifiableField controlField) {
    if(CustomFieldDependencies.UNKNOWN_OPTION.equals(dep.getValuesControllerField())) return;
    EnumFieldType type = getTypeAsEnum();
    if (type == null) return;
    Pair<Long, Function<String, Long>> field_value = getOptVisFieldValue(dep, controlField);
    myCreator.setValue(CustomField.AT_OPT_VIS_FIELD, field_value.getFirst());
    Function<String, Long> toValue = field_value.getSecond();
    Map<String, String> controlValues = dep.getControlledValues();
    type.updateControlValues(controlValues, toValue);
  }

  private Pair<Long, Function<String, Long>> getOptVisFieldValue(CustomFieldDependencies.Dependency dep, @Nullable ModifiableField controlField) {
    Long field = null;
    Function<String, Long> value = Function.Const.create(null);
    if (controlField != null) {
      // custom field option
      field = controlField.getItem();
      final EnumFieldType controlType = controlField.getTypeAsEnum();
      if (controlType != null) {
        value = new Function<String, Long>() { @Override public Long invoke(String optionName) {
          if (optionName == null) return null;
          long item = controlType.findOrCreateOption(optionName);
          return item > 0L ? item : null;
        }};
      }
    } else {
      // standard Bugzilla field option
      BugzillaAttribute controllerAttr = BugzillaHTMLConstants.STANDARD_FIELD_IDS_IN_DEPENDENCIES_MAP.get(dep.getValuesControllerField());
      BugzillaAttributeLink controlLink = CommonMetadata.ATTR_TO_LINK.get(controllerAttr);
      if (controlLink instanceof ReferenceLink) {
        final ReferenceLink controlRefLink = (ReferenceLink) controlLink;
        field = myCreator.getReader().findMaterialized(controlRefLink.getReferentType());
        value = new Function<String, Long>() { @Override public Long invoke(String optionName) {
          return optionName == null ? null : controlRefLink.getOrCreateReferent(myPM, optionName, myCreator);
        }};
      }
    }
    return Pair.create(field, value);
  }

  boolean trusts(CustomFieldDependencies.Dependency dep, CustomFieldDependencies.Source source) {
    if(dep.isEmpty() && source == CustomFieldDependencies.Source.NEW_BUG) {
      // not trustworthy: dependency info is absent on the New Bug page for fields
      // that cannot be set on new bugs, or depend on Product, Classification, or
      // a CF that cannot be set on new bug; it doesn't mean there are no dependencies.
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "Modifiable field(" + getId() + ")";
  }
}