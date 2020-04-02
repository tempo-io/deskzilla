package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.*;
import com.almworks.api.application.field.CustomFieldsHelper;
import com.almworks.api.application.util.BaseModelKey;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.custom.BugzillaCustomField;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.detach.Lifespan;

import java.util.Collection;
import java.util.List;

public class BugzillaCustomFieldsIO implements BaseModelKey.DataIO<List<ModelKey<?>>> {
  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values, ModelKey<List<ModelKey<?>>> key) {
    BugzillaContext bugzillaContext = BugzillaUtil.getContext(itemServices);
    if (bugzillaContext == null) return;
    BugzillaCustomFields customFields = bugzillaContext.getCustomFields();
    Collection<BugzillaCustomField> fields = customFields.copyAndSortExisting(BugzillaCustomField.COMPARATOR);
    CustomFieldsHelper.extractCustomFields(key, bugzillaContext, values, fields, itemVersion, itemServices);
  }

  public void addChanges(UserChanges changes, ModelKey<List<ModelKey<?>>> key) {
  }

  public <SM> SM getModel(Lifespan life, final ModelMap model, final ModelKey<List<ModelKey<?>>> key,
    Class<SM> aClass)
  {
    assert false;
    return null;
  }
}
