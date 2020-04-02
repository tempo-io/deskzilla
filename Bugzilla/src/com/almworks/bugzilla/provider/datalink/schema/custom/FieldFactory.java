package com.almworks.bugzilla.provider.datalink.schema.custom;

import com.almworks.api.application.ModelKey;
import com.almworks.bugzilla.provider.BugzillaCustomFields;
import com.almworks.bugzilla.provider.custom.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DP;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.VersionSource;
import com.almworks.items.sync.impl.AttributeInfo;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.util.Date;
import java.util.List;

public abstract class FieldFactory {

  public static final FieldFactory TEXT = new FieldFactory() {
    protected BugzillaCustomField<?, ?> priCreateField(DBAttribute<?> attribute, String id, String displayName, Boolean availableOnSubmit,
      int order, BugzillaCustomFields fields, VersionSource db, ModelKey prevKey)
    {
      return new Text.TextField((DBAttribute<String>) attribute, id, displayName, availableOnSubmit, order, fields, prevKey);
    }
  };

  public static final FieldFactory LARGE_TEXT = new FieldFactory() {
    protected BugzillaCustomField<?, ?> priCreateField(DBAttribute<?> attribute, String id, String displayName, Boolean availableOnSubmit,
      int order, BugzillaCustomFields fields, VersionSource db, ModelKey prevKey)
    {
      return new Text.TextArea((DBAttribute<String>) attribute, id, displayName, availableOnSubmit, order, fields, prevKey);
    }
  };

  public static final FieldFactory BUG_ID = new FieldFactory() {
    @Override
    protected BugzillaCustomField<?, ?> priCreateField(DBAttribute<?> attribute, String id, String displayName,
      Boolean availableOnSubmit, int order, BugzillaCustomFields fields, VersionSource db,
      ModelKey prevKey)
    {
      return new BugID((DBAttribute<Long>) attribute, id, displayName, availableOnSubmit, fields, order, prevKey);
    }
  };

  public static final FieldFactory DATE = new FieldFactory() {
    @Override
    protected BugzillaCustomField<?, ?> priCreateField(DBAttribute<?> attribute, String id, String displayName,
      Boolean availableOnSubmit, int order, BugzillaCustomFields fields, VersionSource db,
      ModelKey prevKey)
    {
      return new DateTime((DBAttribute<Date>) attribute, id, displayName, availableOnSubmit, order, fields, prevKey);
    }
  };

  public static final FieldFactory MULTI_ENUM = new FieldFactory() {
    @Override
    protected BugzillaCustomField<?, ?> priCreateField(DBAttribute<?> attribute, String id, String displayName,
      Boolean availableOnSubmit, int order, BugzillaCustomFields fields, VersionSource db,
      ModelKey prevKey)
    {
      BoolExpr<DP> optionFilter = CustomField.getOptionFilter(attribute, db);
      return new MultiSelect((DBAttribute<List<Long>>) attribute, id, displayName, availableOnSubmit, order, fields, optionFilter,
        prevKey);
    }
  };

  public static final FieldFactory SINGLE_ENUM = new FieldFactory() {
    @Override
    protected BugzillaCustomField<?, ?> priCreateField(DBAttribute<?> attribute, String id, String displayName,
      Boolean availableOnSubmit, int order, BugzillaCustomFields fields, VersionSource db,
      ModelKey prevKey)
    {
      BoolExpr<DP> optionFilter = CustomField.getOptionFilter(attribute, db);
      return new DropDown((DBAttribute<Long>) attribute, id, displayName, availableOnSubmit, order, fields, optionFilter,
        prevKey);
    }
  };

  public static final FieldFactory NONE = new FieldFactory() {
    @Override
    protected BugzillaCustomField<?, ?> priCreateField(DBAttribute<?> attribute, String id, String displayName,
      Boolean availableOnSubmit, int order, BugzillaCustomFields fields, VersionSource db,
      ModelKey prevKey)
    {
      return null;
    }
  };

  public final BugzillaCustomField<?, ?> createField(ItemVersion attr, String id, String displayName, Boolean availableOnSubmit, int order,
    BugzillaCustomFields fields, @Nullable BugzillaCustomField prevField) {
    ModelKey prevKey;
    DBAttribute<?> attribute = AttributeInfo.getAttribute(attr);
    if (attribute == null) return null;
    if (prevField == null) prevKey = null;
    else if (!Util.equals(attribute, prevField.getAttribute())) prevKey = null;
    else prevKey = prevField.getModelKey();
    BugzillaCustomField<?, ?> newField =
      priCreateField(attribute, id, displayName, availableOnSubmit, order, fields, attr, prevKey);
    if (newField == null || prevField == null) return newField;
    if (newField.getClass().equals(prevField.getClass()) &&
      Util.equals(newField.getAttribute(), prevField.getAttribute()) &&
      Util.equals(newField.getId(), prevField.getId()) &&
      Util.equals(newField.getDisplayName(), prevField.getDisplayName()) &&
      Util.equals(availableOnSubmit, prevField.isAvailableOnSubmit()) &&
      order == prevField.getOrder()) return prevField;
    return newField;
  }

  protected abstract BugzillaCustomField<?, ?> priCreateField(DBAttribute<?> attribute, String id, String displayName, Boolean availableOnSubmit, int order,
    BugzillaCustomFields fields, VersionSource db, @Nullable ModelKey prevKey);
}
