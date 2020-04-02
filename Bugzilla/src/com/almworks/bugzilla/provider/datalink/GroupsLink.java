package com.almworks.bugzilla.provider.datalink;

import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.schema.Group;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import com.almworks.util.Pair;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.util.*;

public class GroupsLink implements DataLink {
  public void updateRevision(PrivateMetadata pm, ItemVersionCreator bugCreator, BugInfo bugInfo, @NotNull BugzillaContext context) {
    final List<Pair<BugGroupData, Boolean>> groups = bugInfo.getGroups();
    if(groups == null) {
      // no data
      return;
    }

    Set<Long> newAGroups = null;
    Set<Long> newCGroups = null;
    for(final Pair<BugGroupData, Boolean> group : groups) {
      final long groupItem = Group.getGroupItem(group.getFirst(), pm, bugCreator);
      final Boolean contained = group.getSecond();
      if (contained == null) {
        assert false : group;
        continue;
      }

      if (newAGroups == null) newAGroups = Collections15.linkedHashSet();
      newAGroups.add(groupItem);

      if (contained) {
        if (newCGroups == null) newCGroups = Collections15.linkedHashSet();
        newCGroups.add(groupItem);
      }
    }

    bugCreator.setValue(Group.AVAILABLE_GROUPS, newAGroups);
    bugCreator.setValue(Group.CONTAINING_GROUPS, newCGroups);
  }


  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info) throws UploadNotPossibleException
  {
    if(bug.isChanged(Group.CONTAINING_GROUPS)) {
      final Set<Long> newGroups = Util.NN(bug.getNewerValue(Group.CONTAINING_GROUPS), Collections.EMPTY_SET);
      final Set<Long> oldGroups = Util.NN(bug.getElderValue(Group.CONTAINING_GROUPS), Collections.EMPTY_SET);
      adjust(bug.getReader(), info, oldGroups, newGroups, true);
      adjust(bug.getReader(), info, newGroups, oldGroups, false);
    }
  }

  private static void adjust(DBReader reader, BugInfoForUpload info, Set<Long> from, Set<Long> to, boolean belongsTo) {
    for(final Long group : to) {
      if(!from.contains(group)) {
        info.changeGroup(readGroupData(group, reader), belongsTo);
      }
    }
  }

  private static BugGroupData readGroupData(long group, DBReader reader) {
    return new BugGroupData(
      reader.getValue(group, Group.FORM_ID),
      reader.getValue(group, Group.NAME),
      reader.getValue(group, Group.DESCRIPTION));
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    final Map<BugGroupData, Boolean> groupChanges = updateInfo.getGroupChanges();
    if(groupChanges.size() == 0) {
      return null;
    }

    final List<Pair<BugGroupData, Boolean>> groups = newInfo.getGroups();
    if(groups == null) {
      // must be called with all info loaded
      assert false;
      return null;
    }

    for (Pair<BugGroupData, Boolean> group : groups) {
      Boolean requestedValue = groupChanges.get(group.getFirst());
      Boolean realValue = group.getSecond();
      if (requestedValue != null && !Util.equals(requestedValue, realValue)) {
        return "groups";
      }
    }

    // there's a special case, where the user has requested update to group A
    // but it happened that the group has been removed or become implicit
    // in this case we don't report error, because there's nothing user can do
    // probably we should display a warning

    return null;
  }

  @Override
  public void initializePrototype(ItemVersionCreator prototype, PrivateMetadata pm) {
  }
}
