package com.almworks.bugzilla.provider.datalink;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.items.api.DBAttribute;

public class IDLink<T> extends ScalarLink<T> {
  public IDLink(DBAttribute<T> attribute) {
    super(attribute, BugzillaAttribute.ID, false, false, false);
  }
}
