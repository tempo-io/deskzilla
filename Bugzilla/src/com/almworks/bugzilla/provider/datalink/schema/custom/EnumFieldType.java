package com.almworks.bugzilla.provider.datalink.schema.custom;

import com.almworks.bugzilla.integration.data.CustomFieldDependencies;
import com.almworks.bugzilla.integration.data.CustomFieldType;
import com.almworks.engine.items.DatabaseUnwrapper;
import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.LongSet;
import com.almworks.util.commons.Function;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.almworks.util.Collections15.emptyList;
import static org.almworks.util.Collections15.hashMap;

class EnumFieldType extends FieldType {
  private final ModifiableField myField;
  private final EnumOptions myEnumOptions = new EnumOptions();

  EnumFieldType(DBAttribute.ScalarComposition composition, String typeId, DBIdentifiedObject kind,
    ModifiableField field, CustomFieldType type) {
    super(Long.class, composition, typeId, kind, type);
    myField = field;
  }

  @Override
  public void updateField(ModifiableField field, List<String> optValues, boolean strictInfos) {
    if (optValues == null) optValues = emptyList();
    LongSet toRemove = myEnumOptions.getAllOptionItems();
    int order = 1;
    for (String option : optValues) {
      ItemVersion optionCreator = myEnumOptions.findOrCreate(option, order);
      order += 1;
      toRemove.remove(optionCreator.getItem());
    }
    if (strictInfos) SyncUtils.deleteAll(getDrain(), toRemove);
  }

  @Override
  public boolean acceptsRawValue(@Nullable List<String> values) {
    if(values == null || values.isEmpty()) {
      return true;
    }
    if(getComposition() == DBAttribute.ScalarComposition.SCALAR) {
      return values.size() == 1;
    }
    return true;
  }

  @SuppressWarnings({"unchecked"})
  @Override
  public void setRawValue(ItemVersionCreator bug, DBAttribute<?> attribute, List<String> values) {
    if (values == null || values.isEmpty()) {
      bug.setValue(attribute, null);
      return;
    }
    LongArray array = new LongArray();
    boolean missed = false;
    for (String value : values) {
      ItemVersion item = myEnumOptions.findOrCreate(value);
      if (item != null) array.add(item.getItem());
       else {
        Log.error("Option not found " + value);
        missed = true;
      }
    }
    switch (attribute.getComposition()) {
    case SCALAR:
      if (array.size() != 1) {
        if (!missed) Log.error("Wrong value count " + values);
      } else bug.setValue((DBAttribute<Long>)attribute, array.get(0));
      break;
    case SET:
    case LIST:
      bug.setSet((DBAttribute<? extends Collection<? extends Long>>) attribute, array);
      break;
    default: Log.error("Unknown composition " + attribute);
    }
  }

  private DBDrain getDrain() {
    return myField.getCreator();
  }

  public long findOrCreateOption(String option) {
    return myEnumOptions.findOrCreate(option).getItem();
  }

  public void updateControlValues(Map<String, String> controlValues, Function<String, Long> toValue) {
    for (Map.Entry<String, ItemVersionCreator> entry : myEnumOptions.ensureOptionsLoaded().entrySet()) {
      String optionName = entry.getKey();
      ItemVersionCreator option = entry.getValue();
      if (option.isInvisible()) continue;
      String controlOptionName = controlValues.get(optionName);
      if (!CustomFieldDependencies.UNKNOWN_OPTION.equals(controlOptionName))
        option.setValue(CustomField.AT_OPT_VIS_VALUE, toValue.invoke(controlOptionName));
    }
  }

  private class EnumOptions {
    private Map<String, ItemVersionCreator> myOptions = null;

    public Map<String, ItemVersionCreator> ensureOptionsLoaded() {
      if (myOptions == null) {
        BoolExpr<DP> optionsExpr = DPEquals.create(CustomField.AT_OPT_FIELD, myField.getItem());
        LongList optionsWithRemoved = DatabaseUnwrapper.query(myField.getCreator().getReader(), optionsExpr).copyItemsSorted();
        Map<String, ItemVersionCreator> options = hashMap();
        for (ItemVersionCreator opt : getDrain().changeItems(optionsWithRemoved)) {
          String value = opt.getValue(CustomField.AT_OPT_VALUE);
          if (value == null) {
            Log.error("Missing option name " + opt);
            opt.delete();
          }
          options.put(value, opt);
        }
        myOptions = options;
      }
      return myOptions;
    }

    public ItemVersion findOrCreate(String option, int order) {
      ItemVersionCreator item = priFindOrCreate(option);
      item.setValue(CustomField.AT_OPT_ORDER, order);
      return item;
    }

    public ItemVersion findOrCreate(String option) {
      return priFindOrCreate(option);
    }

    private ItemVersionCreator priFindOrCreate(String option) {
      Map<String, ItemVersionCreator> options = ensureOptionsLoaded();
      ItemVersionCreator creator = options.get(option);
      if (creator != null) creator.setAlive();
      else {
        creator = getDrain().createItem();
        creator.setValue(DBAttribute.TYPE, CustomField.typeOption);
        creator.setValue(SyncAttributes.CONNECTION, myField.getPM().getConnectionRef());
        creator.setValue(CustomField.AT_OPT_FIELD, myField.getItem());
        creator.setValue(CustomField.AT_OPT_VALUE, option);
        options.put(option, creator);
      }
      return creator;
    }

    public LongSet getAllOptionItems() {
      Map<String, ItemVersionCreator> options = ensureOptionsLoaded();
      LongSet items = new LongSet();
      for (ItemVersionCreator creator : options.values()) items.add(creator.getItem());
      return items;
    }
  }
}
