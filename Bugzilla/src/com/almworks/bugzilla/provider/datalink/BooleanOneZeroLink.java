package com.almworks.bugzilla.provider.datalink;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.ItemVersion;
import org.almworks.util.Log;

/**
 * :todoc:
 *
 * @author sereda
 */
public class BooleanOneZeroLink extends ScalarLink<Boolean> {
  public BooleanOneZeroLink(DBAttribute<Boolean> attribute, BugzillaAttribute bugzillaAttribute, boolean ignoreEmpty) {
    super(attribute, bugzillaAttribute, ignoreEmpty, false, false);
  }

  @Override
  protected Boolean getValueFromRemoteString(String stringValue, BugInfo bugInfo) {
    boolean bool = stringValue != null && stringValue.equals("1");
    if (stringValue != null && !bool) {
      Log.debug("strange: value for attribute " + myBugzillaAttribute.getName() + " is " + stringValue);
    }
    return bool;
  }

  public String createRemoteString(Boolean value, ItemVersion lastServerVersion) {
    boolean b = value != null && value;
    return b ? "1" : "0";
  }

  @Override
  public Boolean getPrototypeValue(PrivateMetadata privateMetadata, DBReader reader) {
    return Boolean.FALSE;
  }
}
