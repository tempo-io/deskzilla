package com.almworks.bugzilla.provider.datalink.schema;

import com.almworks.api.application.ItemKey;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.explorer.qbuilder.filter.EnumNarrower;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.components.CanvasRenderer;

import java.util.Set;

public class MultiEnumAttribute {
  public static final MultiEnumAttribute CC = set(User.ENUM_USERS, BugzillaAttribute.CC, "ccList", "cc", User.RENDERER);

  private final EnumType myEnumType;
  private final BugzillaAttribute myBzAttribute;
  private final DBAttribute<Set<Long>> myBugAttribute;
  private final CanvasRenderer<ItemKey> myValueRenderer;

  public MultiEnumAttribute(EnumType enumType, DBAttribute<Set<Long>> bugAttribute, BugzillaAttribute bzAttribute,
    CanvasRenderer<ItemKey> valueRenderer)
  {
    myEnumType = enumType;
    myBugAttribute = bugAttribute;
    myBzAttribute = bzAttribute;
    myValueRenderer = valueRenderer;
  }

  private static MultiEnumAttribute set(EnumType type, BugzillaAttribute bzAttribute, String subNS, String name,
    CanvasRenderer<ItemKey> valueRenderer) {
    return new MultiEnumAttribute(type, Bug.BUG_NS.linkSet(subNS, name, true), bzAttribute, valueRenderer);
  }

  public final DBAttribute<Set<Long>> getBugAttribute() {
    return myBugAttribute;
  }

  public final String getDisplayableFieldName() {
    return BugzillaUtil.getDisplayableFieldName(myBzAttribute);
  }

  public final EnumType getEnumType() {
    return myEnumType;
  }

  public void registerEnum(CommonMetadata.Registrator registrator) {
    BaseEnumConstraintDescriptor descriptor = myEnumType.multiValueDescriptor(
      registrator.getMd(), myBugAttribute, EnumNarrower.DEFAULT, getDisplayableFieldName(), myValueRenderer);
    registrator.register(myBzAttribute, myBugAttribute, descriptor);
  }
}
