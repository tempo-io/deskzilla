package com.almworks.bugzilla.provider.datalink;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBItemType;

/**
 * :todoc:
 *
 * @author sereda
 */
public class DefaultReferenceLink extends SingleReferenceLink<String> {
  private final boolean myCheckUpdate;

  public DefaultReferenceLink(DBAttribute<Long> referenceLinkSingletonsDBAttribute,
    BugzillaAttribute bugzillaAttribute, DBItemType referentType, DBAttribute<String> referentUniqueKey,
    DBAttribute<String> referentVisualKey, boolean ignoreEmpty, String defaultValue, boolean inPrototype,
    boolean checkUpdate)
  {
    super(referenceLinkSingletonsDBAttribute, bugzillaAttribute, referentType, referentUniqueKey, referentVisualKey,
      ignoreEmpty, defaultValue, inPrototype);
    myCheckUpdate = checkUpdate;
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    return myCheckUpdate ? super.detectFailedUpdate(newInfo, updateInfo, privateMetadata) : null;
  }
}
