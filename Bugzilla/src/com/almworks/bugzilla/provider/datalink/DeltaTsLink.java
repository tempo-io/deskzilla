package com.almworks.bugzilla.provider.datalink;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.items.api.DBAttribute;

public class DeltaTsLink extends ScalarLink<String> {
  public DeltaTsLink(DBAttribute<String> attribute) {
    super(attribute, BugzillaAttribute.DELTA_TS, true, false, false);
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    return null;
  }
}
