package com.almworks.bugzilla.provider;

import com.almworks.api.application.field.ConnectionFieldsManager;
import com.almworks.bugzilla.provider.custom.BugzillaCustomField;
import com.almworks.bugzilla.provider.datalink.schema.custom.CustomField;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.util.SyncUtils;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

public class BugzillaCustomFields extends ConnectionFieldsManager<BugzillaCustomField> {
  private final PrivateMetadata myPm;

  public BugzillaCustomFields(PrivateMetadata pm) {
    super(CustomField.getCFAttributeFilter(pm));
    myPm = pm;
  }

  @Override
  @Nullable
  protected BugzillaCustomField createNonStartedOrUpdateField(DBReader reader, long fieldItem, @Nullable BugzillaCustomField prevField) {
    ItemVersion field = SyncUtils.readTrunk(reader, fieldItem);
    BugzillaCustomField newField = CustomField.createCustomField(field, this, prevField);
    if (newField == null) return null;
    if (prevField == null || shouldReplace(prevField, newField, reader)) return newField;
    prevField.updateField(reader);
    return prevField;
  }

  private boolean shouldReplace(BugzillaCustomField oldField, BugzillaCustomField newField, DBReader reader) {
    return !Util.equals(oldField, newField) || !Util.equals(oldField.getDisplayName(), newField.getDisplayName());
  }

  public CommonMetadata getCommonMetadata() {
    return myPm.getCommonMetadata();
  }
}
