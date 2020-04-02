package com.almworks.bugzilla.provider.datalink.schema.custom;

import com.almworks.bugzilla.integration.BugzillaDateUtil;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.VersionSource;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

interface FieldUploader<T> {
  void buildUploadInfo(BugInfoForUpload info, ItemVersion bug, @NotNull DBAttribute<T> attribute);

  abstract class CustomFieldUploader<T> implements FieldUploader<T> {
    @Override
    public void buildUploadInfo(BugInfoForUpload info, ItemVersion bug, @NotNull DBAttribute<T> attribute) {
      String cfId = CustomField.getFieldId(bug.getReader(), attribute);
      if (cfId == null) {
        Log.error("Not a custom field " + attribute);
        return;
      }
      T value = bug.getValue(attribute);
      List<String> list = convertToUpload(bug, value);
      if (list == null) list = Collections15.emptyList();
      info.changeCustomField(cfId, list);
    }

    protected abstract List<String> convertToUpload(VersionSource db, T value);
  }

  FieldUploader<Long> CF_BUG_ID = new CustomFieldUploader<Long>() {
    @Override
    protected List<String> convertToUpload(VersionSource db, Long value) {
      return Collections.singletonList(value == null ? "" : String.valueOf(db.forItem(value).getValue(Bug.attrBugID)));
    }
  };

  FieldUploader<Date> CF_DATE = new CustomFieldUploader<Date>() {
    @Override
    protected List<String> convertToUpload(VersionSource db, Date value) {
      String dateString = "";
      if (value != null && value.getTime() > Const.DAY) {
        dateString = BugzillaDateUtil.CUSTOM_DATE_FIELD_FORMAT.format(value);
      }
      return Collections.singletonList(dateString);
    }
  };

  FieldUploader<Collection<Long>> CF_MULTI_ENUM = new CustomFieldUploader<Collection<Long>>() {
    @Override
    protected List<String> convertToUpload(VersionSource db, Collection<Long> value) {
      if(value == null) {
        return Collections15.emptyList();
      }
      List<String> change = Collections15.arrayList();
      for (Long item : value) {
        String s = CustomField.getOptionName(db.forItem(item));
        if (!s.isEmpty()) {
          change.add(s);
        }
      }
      return change;
    }
  };

  FieldUploader<Long> CF_SINGLE_ENUM = new CustomFieldUploader<Long>() {
    @Override
    protected List<String> convertToUpload(VersionSource db, Long value) {
      return Collections.singletonList(value == null ? "" : CustomField.getOptionName(db.forItem(value)));
    }
  };

  FieldUploader<String> CF_TEXT = new CustomFieldUploader<String>() {
    @Override
    protected List<String> convertToUpload(VersionSource db, String value) {
      return Collections.singletonList(Util.NN(value));
    }
  };

  FieldUploader CF_UNKNOWN = new FieldUploader() {
    @Override
    public void buildUploadInfo(BugInfoForUpload info, ItemVersion bug, @NotNull DBAttribute attribute) {
      Log.warn("cannot upload field [Unknown][" + CustomField.getFieldId(bug.getReader(), attribute) + "]");
    }
  };
}
