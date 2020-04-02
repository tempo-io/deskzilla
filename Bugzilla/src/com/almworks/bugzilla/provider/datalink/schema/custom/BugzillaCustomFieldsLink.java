package com.almworks.bugzilla.provider.datalink.schema.custom;

import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.DataLink;
import com.almworks.bugzilla.provider.datalink.UploadNotPossibleException;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.*;
import com.almworks.util.collections.LongSet;
import com.almworks.util.collections.MultiMap;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.*;

class BugzillaCustomFieldsLink implements DataLink {
  public BugzillaCustomFieldsLink() {
  }

  public void updateRevision(PrivateMetadata privateMetadata, ItemVersionCreator bugCreator, BugInfo bugInfo, @NotNull BugzillaContext context)
  {
    @Nullable MultiMap<String, String> cfvalues = bugInfo.getCustomFieldValues();
    @Nullable Map<String, CustomFieldInfo> cfinfo = bugInfo.getCustomFieldInfo();

    LongSet updated = new LongSet();
    if (cfvalues != null) {
      Set<String> fieldSet = cfvalues.keySet();
      if (cfinfo != null) {
        Set<String> s = Collections15.linkedHashSet();
        s.addAll(fieldSet);
        s.addAll(cfinfo.keySet());
        fieldSet = s;
      }
      for (String cfid : fieldSet) {
        List<String> valueList = cfvalues.getAll(cfid);
        CustomFieldInfo info = cfinfo == null ? null : cfinfo.get(cfid);
        updated.add(CustomField.setFieldValue(cfid, info, bugCreator, privateMetadata, valueList));
      }
    }
    CustomField.clearOtherFields(bugCreator, privateMetadata, updated);
  }

  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info) throws
    UploadNotPossibleException
  {
    for (DBAttribute attribute : CustomField.selectCustomFields(bug.getNewerVersion(), bug.getChanged())) 
      CustomField.buildUploadInfo(info, bug.getNewerVersion(), attribute);
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    // todo not implemented
    // decided in favor of time for deskzilla 2.0

    return null;

/*
    Map<String, List<String>> changes = updateInfo.getCustomFieldChanges();
    if (changes == null)
      return null;

    @Nullable MultiMap<String, String> values = newInfo.getCustomFieldValues();
    if (values == null)
      return null;

    for (Map.Entry<String,List<String>> entry : changes.entrySet()) {
      String id = entry.getKey();
      String value = Util.NN(entry.getValue());
      if (value.length() == 0) {
        // clear
        if (values != null && Util.NN(values.getLast(id)).length() > 0) {
          return "custom field " + id;
        }
      } else {
        if (values == null || !Util.equals(values.getLast(id), value)) {
          return "custom field " + id;
        }
      }
    }

    return null;
*/
  }

  @Override
  public void initializePrototype(ItemVersionCreator prototype, PrivateMetadata pm) {
  }
}
