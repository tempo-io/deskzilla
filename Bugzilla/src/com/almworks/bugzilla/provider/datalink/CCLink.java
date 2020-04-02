package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.constraint.OneFieldConstraint;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import org.almworks.util.Collections15;

import java.util.Set;

/**
 * :todoc:
 *
 * @author sereda
 */
public class CCLink extends ReferenceArrayLink<Set<Long>> {
  public static final CCLink INSTANCE = new CCLink();

  private CCLink() {
    super(Bug.attrCcList, BugzillaAttribute.CC, User.type, User.EMAIL, User.DISPLAY_NAME);
  }

  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info) {
    final UniqueConvertor uc = new UniqueConvertor(bug.getReader());
    final Set<String> newCC = uc.collectSet(bug.getNewerValue(getAttribute()));
    final Set<String> lastCC = uc.collectSet(bug.getElderValue(getAttribute()));

    // performing diff
    final Set<String> keepCC = Collections15.hashSet(lastCC);
    keepCC.retainAll(newCC);
    newCC.removeAll(keepCC);
    lastCC.removeAll(keepCC);

    info.removeCC(lastCC);
    info.addCC(newCC);
  }

  @Override
  public ItemProxy createProxy(PrivateMetadata pm, String keyValue) {
    return super.createProxy(pm, User.normalizeEmailId(keyValue));
  }

  @Override
  protected Set<Long> createContainer() {
    return Collections15.hashSet();
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
/*
    String[] newValues = newInfo.getValues().getTupleValues(getBugzillaAttribute());
    makeUniqueKeyArray(newValues, privateMetadata);
    Set<String> removeCC = updateInfo.getRemoveCC();
    Set<String> addCC = Collections15.hashSet(updateInfo.getAddCC());
    for (int i = 0; i < newValues.length; i++) {
      String cc = newValues[i];
      if (removeCC.contains(cc))
        return "cc[" + cc + "]";
      addCC.remove(cc);
    }
    if (addCC.size() > 0)
      return "cc[" + addCC.iterator().next() + "]";
*/
    // the server is free to substitute any user
    return null;
  }

  @Override
  public BooleanChart.Element buildBooleanChartElement(
    OneFieldConstraint constraint, boolean negated, DBReader reader, BugzillaConnection connection)
  {
    if (negated) {
      // bugzilla cannot have such search condition - we need to negate the whole boolean chart for this
      return null;
    }
    return super.buildBooleanChartElement(constraint, negated, reader, connection);
  }

  @Override
  public String getLocalString(ItemVersion referent, BugzillaConnection connection) throws BadReferent {
    final String email = super.getLocalString(referent, connection);
    return User.stripSuffix(email, connection);
  }
}
