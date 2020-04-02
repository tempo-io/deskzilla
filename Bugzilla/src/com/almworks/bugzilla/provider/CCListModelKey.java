package com.almworks.bugzilla.provider;

import com.almworks.api.application.*;
import com.almworks.api.explorer.gui.ItemModelKey;
import com.almworks.api.explorer.gui.ItemsListKey;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.bugzilla.provider.datalink.schema.MultiEnumAttribute;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.explorer.qbuilder.filter.EnumNarrower;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.*;
import org.almworks.util.detach.Lifespan;

import java.util.Collection;
import java.util.Set;

public class CCListModelKey extends ItemsListKey implements ItemModelKey<Collection<ItemKey>> {
  private final TypedKey<VariantsModelFactory> myVariantsKey;

  public CCListModelKey() {
    super(MultiEnumAttribute.CC.getBugAttribute(), MultiEnumAttribute.CC.getEnumType().getResolver(), MultiEnumAttribute.CC.getDisplayableFieldName());
    myVariantsKey = TypedKey.create(getName() + "#variants");
  }

  @Override
  public void addChanges(UserChanges changes) {
    Collection<ItemKey> newValue = changes.getNewValue(this);
    if (newValue == null || newValue.isEmpty()) {
      changes.getCreator().setValue(getAttribute(), Collections15.<Long>emptySet());
      return;
    }
    Set<Long> r = Collections15.hashSet();
    for (ItemKey user : newValue) {
      long artifact = user.getResolvedItem();
      if (artifact <= 0) artifact = User.ENUM_USERS.findOrCreate(changes, user.getId());
      if (artifact > 0) r.add(artifact);
    }
    changes.getCreator().setValue(getAttribute(), r);
  }

  @Override
  protected ItemKey extractValueFrom(Long item, ItemVersion primaryItem, LoadedItemServices itemServices) {
    return itemServices.getItemKeyCache().getItemKeyOrNull(item, primaryItem.getReader(), User.KEY_FACTORY);
  }

  @Override
  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    super.extractValue(itemVersion, itemServices, values);
    ItemHypercube cube = itemServices.getConnectionCube();
    BugzillaConnection connection = itemServices.getConnection(BugzillaConnection.class);
    if(connection == null) {
      Log.error("Missing Bugzilla connection");
      return;
    }
    VariantsModelFactory factory = User.ENUM_USERS.getVariantsFactory(connection.getCommonMD(), cube, EnumNarrower.DEFAULT);
    values.put(myVariantsKey, factory);
  }

  public void copyValue(ModelMap to, PropertyMap from) {
    super.copyValue(to, from);
    to.put(myVariantsKey, from.get(myVariantsKey));
  }

  public void takeSnapshot(PropertyMap to, ModelMap from) {
    super.takeSnapshot(to, from);
    to.put(myVariantsKey, from.get(myVariantsKey));
  }

  public AListModel<ItemKey> getModelVariants(ModelMap model, Lifespan life) {
    VariantsModelFactory factory = model.get(myVariantsKey);
    if (factory == null)
      return AListModel.EMPTY;
    return factory.createModel(life);
  }
}
