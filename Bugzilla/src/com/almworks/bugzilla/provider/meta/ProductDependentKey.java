package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ResolvedItem;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.ProductDependenciesTracker;
import com.almworks.bugzilla.provider.ProductDependencyInfo;
import com.almworks.bugzilla.provider.datalink.schema.SingleEnumAttribute;
import com.almworks.util.properties.PropertyMap;

import java.util.List;

import static org.almworks.util.Collections15.arrayList;

public class ProductDependentKey extends ComboBoxModelKey {
  private final ComboBoxModelKey myProductKey;
  private final BugzillaAttribute myBzAttribute;

  public ProductDependentKey(ComboBoxModelKey productKey, SingleEnumAttribute enumAttr)
  {
    super(enumAttr, null);
    myProductKey = productKey;
    myBzAttribute = enumAttr.getBugzillaAttribute();
  }

  @Override
  public List<ResolvedItem> getApplicableVariantsList(PropertyMap values) {
    ItemKey product = myProductKey.getValue(values);
    ProductDependenciesTracker tracker = product == null ? null : ProductDependenciesKey.KEY.getValue(values);
    ProductDependencyInfo info = tracker == null ? null : tracker.getInfo(product);
    return info != null
      ? arrayList(info.getValidValues(myBzAttribute))
      : super.getApplicableVariantsList(values);
  }
}
