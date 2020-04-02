package com.almworks.bugzilla.provider.custom;

import com.almworks.api.application.*;
import com.almworks.api.application.field.ItemField;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.application.util.BaseKeyBuilder;
import com.almworks.api.application.util.BaseModelKey;
import com.almworks.api.explorer.util.ConnectContext;
import com.almworks.bugzilla.provider.BugzillaCustomFields;
import com.almworks.bugzilla.provider.datalink.schema.custom.CustomField;
import com.almworks.bugzilla.provider.datalink.schema.custom.LoadedField;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.collections.Containers;
import com.almworks.util.models.*;
import com.almworks.util.threads.CanBlock;
import org.almworks.util.*;
import org.almworks.util.detach.Lifecycle;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Comparator;
import java.util.List;

// todo review: copy of jira's CFA?
public abstract class BugzillaCustomField<T, S> implements ItemField<T, S> {
  // used to get BCF from ModelKey
  public static final TypedKey<BugzillaCustomField> SOURCE_CUSTOM_FIELD = TypedKey.create("SCF");
  public static final Comparator<BugzillaCustomField> COMPARATOR = new MyComparator();

  @NotNull
  private final DBAttribute<S> myAttribute;

  @NotNull
  private final String myId;

  @Nullable
  private final String myDisplayName;

  @Nullable
  private final Boolean myAvailableOnSubmit;

  private final int myOrder;

  private final BugzillaCustomFields myFields;

  private TableColumnAccessor<LoadedItem, T> myColumn;
  private ConstraintDescriptor myConstraintDescriptor;
  private ModelKey<T> myKey;
  private volatile boolean myStarted;

  private final Lifecycle myLife = new Lifecycle(false);

  /**
   * defines when this field should be visible
   */
  private VisibilityConstraint myFieldVisibility;

  /**
   * Field that defines which values should be shown
   */
  private LoadedField myOptionVisibilityField;

  public BugzillaCustomField(DBAttribute<S> attribute, String id, String displayName, Boolean availableOnSubmit,
    BugzillaCustomFields fields, int order, @Nullable ModelKey<T> modelKey)
  {
    myAttribute = attribute;
    myId = id;
    myDisplayName = displayName;
    myAvailableOnSubmit = availableOnSubmit;
    myFields = fields;
    myOrder = order;
    myKey = modelKey;
  }

  public void start(DBReader reader) {
    assert !myStarted;
    updateVisibilityData(reader);
    myConstraintDescriptor = createDescriptor();
    getModelKey();
    myStarted = true;
  }

  public void updateField(DBReader reader) {
    updateVisibilityData(reader);
  }

  public void stop() {
    assert myStarted : this;
    if (!myStarted)
      return;
    myLife.dispose();

    ConstraintDescriptor d = myConstraintDescriptor;
    // hack
    if (d instanceof BaseEnumConstraintDescriptor) {
      ((BaseEnumConstraintDescriptor) d).detach();
    }
  }

  @Nullable
  public static <Z> BugzillaCustomField<Z, ?> fromModelKey(ModelKey<Z> modelKey) {
    if (!(modelKey instanceof ModelKeyWithOptionalBehaviors)) return null;
    BugzillaCustomField field = ((ModelKeyWithOptionalBehaviors) modelKey).getOptionalBehavior(SOURCE_CUSTOM_FIELD);
    if (field == null) return null;
    if (field.myLife.isDisposed()) {
      BugzillaCustomField actualField = field.myFields.getByAttributeId(field.getAttribute().getId());
      if (actualField != null) return actualField;
    }
    return field;
  }

  protected Lifespan life() {
    return myLife.lifespan();
  }

  public int getOrder() {
    return myOrder;
  }

  private TableColumnAccessor<LoadedItem, T> createColumn() {
    TableColumnBuilder<LoadedItem, T> builder = TableColumnBuilder.create();
    builder.setSizePolicy(ColumnSizePolicy.Calculated.freeLetterMWidth(10));
    builder.setId(myId);
    builder.setName(getDisplayName());
    builder.setReorderable(true);
    buildColumn(builder);
    return builder.createColumn();
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName == null ? myId : myDisplayName;
  }

  protected abstract void buildColumn(TableColumnBuilder<LoadedItem, T> builder);

  protected ModelKey<T> createKey() {
    BaseKeyBuilder<T> builder = BaseKeyBuilder.create();
    builder.setDisplayName(getDisplayName());
    builder.setMergePolicy(ModelMergePolicy.MANUAL);
    builder.setName(myId);
    builder.setOptionalBehavior(SOURCE_CUSTOM_FIELD, this);
    buildKey(builder);
    return builder.getKey();
  }

  protected abstract void buildKey(BaseKeyBuilder<T> builder);

  public ModelKey<T> getModelKey() {
    if (myKey == null) {
      myKey = createKey();
    } else if (myKey instanceof BaseModelKey) {
      if (!((BaseModelKey) myKey).changeOptionalBehavior(SOURCE_CUSTOM_FIELD, this)) {
        Log.error("No custom field for current key " + myKey + " field: " + this);
      }
    }
    return myKey;
  }

  @NotNull
  public DBAttribute<S> getAttribute() {
    return myAttribute;
  }

  public TableColumnAccessor<LoadedItem, ?> getColumn() {
    if (myColumn == null)
      myColumn = createColumn();
    return myColumn;
  }

  @Nullable
  @CanBlock
  public synchronized ConstraintDescriptor getDescriptor() {
    assert myStarted;
    return myConstraintDescriptor;
  }

  protected abstract ConstraintDescriptor createDescriptor();

  @NotNull
  public String getId() {
    return myId;
  }

  public boolean isMultilineText() {
    return false;
  }

  public abstract JComponent createValueEditor(ConnectContext context);

  public abstract boolean isTextSearchEnabled();

  public synchronized VisibilityConstraint getVisibilityConstraint() {
    return myFieldVisibility;
  }

  private void updateVisibilityData(DBReader reader) {
    myFieldVisibility = CustomField.getFieldVisibility(reader, myAttribute);
    myOptionVisibilityField = CustomField.loadOptionVisibilityField(reader, myAttribute);
  }

  public LoadedField getOptionVisibilityField() {
    return myOptionVisibilityField;
  }

  public static ModelKey<?> findModelKeyByControllerName(String name, List<ModelKey<?>> customFieldKeys) {
    if (name == null || name.length() == 0)
      return null;
    if (name.startsWith("cf_")) {
      for (ModelKey<?> key : customFieldKeys) {
        if (name.equals(key.getName()))
          return key;
      }
      return null;
    } else {
      return BugzillaKeys.getEnumFieldTypeNames().get(name);
    }
  }

  public boolean isAvailableOnSubmit() {
    return Boolean.TRUE.equals(myAvailableOnSubmit);
  }

  public static class ResolvedCustomFieldOption extends ResolvedItem {
    public static final ResolvedFactory<ResolvedCustomFieldOption> FACTORY = new ResolvedFactory<ResolvedCustomFieldOption>() {
      @Override
      public ResolvedCustomFieldOption createResolvedItem(long item, DBReader reader) throws BadItemException {
        ItemVersion option = SyncUtils.readTrunk(reader, item);
        String name = option.getValue(CustomField.AT_OPT_VALUE);
        if (name == null) throw new BadItemException("custom field option with no name", item);
        int iorder = option.getNNValue(CustomField.AT_OPT_ORDER, Integer.MAX_VALUE);
        ItemOrder order = ItemOrder.byOrderAndString(iorder, name);
        VisibilityConstraint visibilityParameters = CustomField.getOptionVisibilityParameters(option);
        long connectionItem = Util.NN(reader.getValue(item, SyncAttributes.CONNECTION), 0L);
        return new ResolvedCustomFieldOption(item, name, order, null, null, visibilityParameters, connectionItem);
      }
    };
    private final VisibilityConstraint myVisibilityParameters;

    private ResolvedCustomFieldOption(long item, @NotNull String representation, ItemOrder order, @Nullable String uniqueKey, @Nullable Icon icon, VisibilityConstraint visibilityConstraint, long connectionItem) {
      super(item, representation, order, uniqueKey, icon, connectionItem);
      myVisibilityParameters = visibilityConstraint;
    }

    public VisibilityConstraint getVisibilityConstraint() {
      return myVisibilityParameters;
    }

    @Override
    public boolean isSame(ResolvedItem that) {
      if (!super.isSame(that)) return false;
      return Util.equals(myVisibilityParameters, ((ResolvedCustomFieldOption) that).myVisibilityParameters);
    }
  }


  private static class MyComparator implements Comparator<BugzillaCustomField> {
    public int compare(BugzillaCustomField o1, BugzillaCustomField o2) {
      return Containers.compareInts(o1.getOrder(), o2.getOrder());
    }
  }
}
