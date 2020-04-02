package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.*;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.*;
import com.almworks.util.English;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.util.Set;

/**
 * Verifies supported product-dependent model keys.
 */
public class ProductDependentMkVerifier implements ModelKeyVerifier {
  private final ModelKey<ItemKey> myProductKey;
  private final ModelKey<ItemKey> myKey;
  private final BugzillaAttribute myAttribute;

  public ProductDependentMkVerifier(ModelKey<ItemKey> productKey, ModelKey<ItemKey> key, BugzillaAttribute attribute) {
    myProductKey = productKey;
    myKey = key;
    myAttribute = attribute;
  }

  @Override
  public String verify(@NotNull PropertyMap item) {
    if (bypassVerification(item)) {
      return null;
    }
    ItemKey product = myProductKey.getValue(item);
    ProductDependenciesTracker depTracker = ProductDependenciesKey.KEY.getValue(item);
    ProductDependencyInfo info = depTracker.getInfo(product);
    if (info == null) {
      Log.warn("PDMKV: no product info for " + product);
      return null;
    }
    ItemKey value = myKey.getValue(item);
    Set<? extends ItemKey> validValues = info.getValidValues(myAttribute);
    if (value != null && isEmptyValue(value) && validValues.isEmpty()) {
      // no allowed values, and no value is selected
      return null;
    }
    if (!validValues.contains(value)) {
      String keyName = English.capitalize(keyNameOrDefault(myKey));
      String wrongValue = value != null ? value.getDisplayName() : "no value is set";
      return keyName + " (" + wrongValue + ')';
    } else {
      return null;
    }
  }

  private boolean bypassVerification(PropertyMap item) {
    if (!myAttribute.isOptional()) return false;
    BugzillaContext ctx = BugzillaUtil.getContext(LoadedItemServices.VALUE_KEY.getValue(item));
    if (ctx == null) {
      assert false : item;
      return true;
    }
    OptionalFieldsTracker tracker = ctx.getOptionalFieldsTracker();
    return tracker.isUnused(myAttribute);
  }

  private boolean isEmptyValue(ItemKey value) {
    String dispValue = value.getDisplayName();
    return myAttribute.isEmptyValue(dispValue) || BugzillaAttribute.NO_MILESTONE.equals(dispValue);
  }

  @Override
  public String verifyEdit(@Nullable PropertyMap oldState, @NotNull PropertyMap newState) {
    if (oldState != null
      && Util.equals(myProductKey.getValue(oldState), myProductKey.getValue(newState))
      && Util.equals(myKey.getValue(oldState), myKey.getValue(newState))
    ) {
      // nothing interesting has changed
      return null;
    }
    return verify(newState);
  }

  private String keyNameOrDefault(ModelKey<ItemKey> key) {
    String name = Util.NN(key.getDisplayableName());
    return name.isEmpty() ? "value" : name;
  }
}
