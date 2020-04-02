package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.*;
import com.almworks.api.explorer.gui.*;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.bugzilla.provider.datalink.schema.SingleEnumAttribute;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.threads.Threads;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.List;

/**
 * @author : Dyoma
 */
public class ComboBoxModelKey extends AbstractComboBoxModelKey implements ItemModelKey<ItemKey> {
  private final SingleEnumAttribute myEnumAttr;
  private final boolean myCanCreateValues;

  public ComboBoxModelKey(SingleEnumAttribute enumAttr, ItemKey nullValue) {
    this(enumAttr, nullValue, false );
  }

  public ComboBoxModelKey(SingleEnumAttribute enumAttr,ItemKey nullValue, boolean canCreateValues) {
    super(enumAttr.getBugAttribute(), nullValue, enumAttr.getDisplayableFieldName(), enumAttr.getMergePolicy(), enumAttr.getFactory());
    myEnumAttr = enumAttr;
    myCanCreateValues = canCreateValues;
  }

  public int compare(ItemKey o1, ItemKey o2) {
    return ItemKey.COMPARATOR.compare(o1, o2);
  }

  public void addExistingChange(UserChanges changes) {
    // was: changes.setValue(getAttribute(), changes.getNewValue(this).getResolvedValue());
    // fix for http://bugzilla/main/show_bug.cgi?id=834
    if (changes == null) {
      assert false : this;
      return;
    }
    DBAttribute<Long> attribute = getAttribute();
    ItemKey newValue = changes.getNewValue(this);
    if (newValue == null) {
      return;
    }

    long resolvedValue = newValue.getResolvedItem();
    if (resolvedValue <= 0) {
      resolvedValue = myCanCreateValues ? resolveOrCreate(newValue, changes) : resolve(newValue, changes);
    }
    if (resolvedValue > 0) {
      changes.getCreator().setValue(attribute, resolvedValue);
    }
  }

  private long resolveOrCreate(ItemKey newValue, UserChanges changes) {
    long resolvedValue = 0L;
    try {
      resolvedValue = newValue.resolveOrCreate(changes);
      if (resolvedValue <= 0) resolvedValue = myEnumAttr.getEnumType().findOrCreate(changes, newValue.getId());
      if (resolvedValue <= 0) {
        changes.invalidValue(this, newValue.getDisplayName());
      }
    } catch (BadItemKeyException e) {
      changes.invalidValue(this, newValue.getDisplayName() + ": " + e.getMessage());
    }
    return resolvedValue;
  }

  private long resolve(ItemKey newValue, UserChanges changes) {
    BaseEnumConstraintDescriptor descriptor = CommonMetadata.getEnumDescriptor(getAttribute());
    if (descriptor == null) { Log.error("CBMK:descr " + this); assert false; return 0L; }
    List<Long> resolution = descriptor.resolveItem(newValue.getId(), changes.getContextHypercube());
    return resolution.size() == 1 ? resolution.get(0) : 0L;
  }

  public void setModelValue(ModelMap model, @Nullable ItemKey newValue) {
    if (newValue != null) {
      long resolution = newValue.getResolvedItem();
      if (resolution == 0) {
        BugzillaConnection c = BugzillaConnection.getInstance(model); if (c == null) { Log.error("CBMK:no c"); return; }
        CommonMetadata md = c.getCommonMD();
        LoadedItemServices itemServices = LoadedItemServices.VALUE_KEY.getValue(model);
        ItemHypercube cube = itemServices.getConnectionCube();
        ResolvedItem resolved = myEnumAttr.resolveItemKey(md, cube, newValue);
        if (resolved != null) newValue = resolved;
      }
    }
    super.setModelValue(model, newValue);
  }

  protected VariantsModelFactory extractVariants(ItemVersion itemVersion, LoadedItemServices itemServices) {
    return myEnumAttr.extractVariants(itemServices);
  }

  public String toString() {
    return "CBMK[" + getDisplayableName() + "]";
  }

  @Override
  public TextResolver getResolver() {
    return null;
  }

  public AListModel<ItemKey> getModelVariants(ModelMap model, Lifespan life) {
    Threads.assertAWTThread();
    return attachVariants(model, life);
  }

  public SingleEnumAttribute getEnumAttr() {
    return myEnumAttr;
  }
}
