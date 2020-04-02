package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.constraint.OneFieldConstraint;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.Set;

public class BugReferenceArrayLink extends AbstractAttributeLink<Set<Long>> {
  public BugReferenceArrayLink(DBAttribute<Set<Long>> attribute, BugzillaAttribute bugzillaAttribute, boolean ignoreEmpty) {
    super(attribute, bugzillaAttribute, ignoreEmpty, true);
  }

  public void updateRevision(final PrivateMetadata pm, ItemVersionCreator bugCreator, BugInfo bugInfo, @NotNull BugzillaContext context) {
    if(cannotTell(bugInfo)) return;
    final List<String> newKeys = bugInfo.getValues().getTupleValues(myBugzillaAttribute);
    final Set<ItemProxy> referents = Collections15.linkedHashSet();
    for(final String key : newKeys) referents.add(Bug.createBugProxy(pm.thisConnectionItem(), key));
    bugCreator.setSet(getAttribute(), Collections15.arrayList(referents));
  }

  @Nullable
  @Override
  protected String createRemoteString(Set<Long> value, ItemVersion lastServerVersion) {
    if(value == null) {
      return null;
    }

    final StringBuilder r = new StringBuilder();
    for(final Long item : value) {
      if(item == null || item <= 0) {
        Log.warn(this + ": bad referent " + item + " in " + value);
        continue;
      }

      final ItemVersion referent = lastServerVersion.forItem(item);
      final Integer id = referent.getValue(Bug.attrBugID);
      if(id == null) {
        Log.warn(this + ": cannot get ID from " + referent);
        continue;
      }

      if(r.length() > 0) {
        r.append(' ');
      }
      r.append(id);
    }

    return r.toString();
  }

  @Nullable
  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    BugzillaAttribute attribute = getBugzillaAttribute();
    String requestedValue = updateInfo.getNewValues().getScalarValue(attribute, null);
    if (requestedValue == null)
      return null;
    List<String> values = newInfo.getValues().getTupleValues(myBugzillaAttribute);
    Set<String> requested;
    requestedValue = requestedValue.trim();
    if (requestedValue.length() == 0)
      requested = Collections15.emptySet();
    else
      requested = Collections15.hashSet(requestedValue.split("\\s+"));
    Set<String> received = Collections15.hashSet(values);
    if (requested.equals(received)) {
      return null;
    } else {
      String receivedString = StringUtil.implode(received, " ");
      return attribute.getName() + ": [" + requestedValue + "] -- [" + receivedString + "]";
    }
  }

  protected String getBCElementParameter(OneFieldConstraint constraint, DBReader reader, BugzillaConnection connection) {
    assert false : constraint;
    return null;
  }
}
