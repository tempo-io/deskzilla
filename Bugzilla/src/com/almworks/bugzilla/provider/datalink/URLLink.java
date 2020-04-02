package com.almworks.bugzilla.provider.datalink;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.items.api.DBAttribute;

/**
 * :todoc:
 *
 * @author sereda
 */
public class URLLink extends ScalarLink<String> {
  private static final String EMPTY_URL = "";

  public URLLink(DBAttribute<String> attribute) {
    super(attribute, BugzillaAttribute.BUG_URL, false, true, false);
  }

  public String getRemoteString(BugInfo bugInfo, PrivateMetadata privateMetadata) {
    String remoteString = super.getRemoteString(bugInfo, privateMetadata);
    if (remoteString == null)
      return null;
    if (remoteString.trim().equalsIgnoreCase(EMPTY_URL))
      return "";
    return remoteString;
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    BugzillaAttribute attribute = getBugzillaAttribute();
    String requestedValue = updateInfo.getNewValues().getScalarValue(attribute, null);
    if (requestedValue == null)
      return null;
    String newValue = newInfo.getValues().getScalarValue(attribute, "");
    if (newValue.equalsIgnoreCase(EMPTY_URL))
      newValue = "";
    if (requestedValue.equalsIgnoreCase(EMPTY_URL))
      requestedValue = "";
    return requestedValue.equals(newValue) ? null : attribute.getName();
  }
}
