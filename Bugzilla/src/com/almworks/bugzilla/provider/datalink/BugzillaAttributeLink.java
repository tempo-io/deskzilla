package com.almworks.bugzilla.provider.datalink;

import com.almworks.bugzilla.integration.BugzillaAttribute;

public interface BugzillaAttributeLink<T> extends AttributeLink<T> {
  BugzillaAttribute getBugzillaAttribute();
}
