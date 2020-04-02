package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.constraint.OneFieldConstraint;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.BugzillaDateUtil;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import org.almworks.util.Const;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.util.Date;
import java.util.Map;

public class DateLink extends ScalarLink<Date> {
  @Nullable
  private final Map<TypedKey<? extends OneFieldConstraint>, ConstraintConvertor<? extends OneFieldConstraint>>
    myConstraintConvertors;

  public DateLink(DBAttribute<Date> attribute, BugzillaAttribute bugzillaAttribute, boolean ignoreEmpty,
    @Nullable Map<TypedKey<? extends OneFieldConstraint>, ConstraintConvertor<? extends OneFieldConstraint>> constraintConvertors,
    boolean inPrototype, boolean readOnly)
  {
    super(attribute, bugzillaAttribute, ignoreEmpty, inPrototype, readOnly);
    myConstraintConvertors = constraintConvertors;
  }

  @Override
  protected Date getValueFromRemoteString(String stringValue, BugInfo bugInfo) {
    return stringValue == null ? null : BugzillaDateUtil.parseOrWarn(stringValue, null, bugInfo.getDefaultTimezone());
  }

  public String createRemoteString(Date value, ItemVersion lastServerVersion) {
    if(myReadonly) {
      return null;
    }
    if(value == null || value.getTime() < Const.DAY) {
      return "";
    }
    return BugzillaDateUtil.DATE_FIELD_FORMAT.format(value);
  }

  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info) {
    if (!myReadonly) {
      super.buildUploadInfo(prepare, bug, info);
    }
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    if (myBugzillaAttribute == BugzillaAttribute.MODIFICATION_TIMESTAMP)
      return null;
    return super.detectFailedUpdate(newInfo, updateInfo, privateMetadata);
  }

//  public Object getPrototypeValue(Workspace workspace, PrivateMetadata privateMetadata) {
//    return null;
//  }

  public BooleanChart.Element buildBooleanChartElement(
    OneFieldConstraint constraint, boolean negated, DBReader reader, BugzillaConnection connection)
  {
    ConstraintConvertor<OneFieldConstraint> convertor = getConvertor(constraint);
    if (convertor == null) {
      assert false : constraint;
      return null;
    }
    return convertor.createElement(constraint, negated);
  }

  @SuppressWarnings({"unchecked"})
  @Nullable
  private <C extends OneFieldConstraint> ConstraintConvertor<C> getConvertor(@NotNull OneFieldConstraint constraint) {
    if (myConstraintConvertors == null)
      return null;
    TypedKey<? extends OneFieldConstraint> type = constraint.getType();
    //noinspection CastToIncompatibleInterface
    return (ConstraintConvertor<C>) myConstraintConvertors.get(type);
  }
}
