package com.almworks.bugzilla.provider.meta.groups;

import com.almworks.api.application.*;
import com.almworks.api.application.util.BaseModelKey;
import com.almworks.bugzilla.provider.datalink.schema.Group;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;

import java.util.Map;
import java.util.Set;

public class GroupsIO implements BaseModelKey.DataIO<BugGroupInfo> {
  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values, ModelKey<BugGroupInfo> modelKey) {
    BugGroupInfo groupInfo = buildGroupInfo(itemVersion);
    modelKey.setValue(values, groupInfo);
  }

  public static BugGroupInfo buildGroupInfo(ItemVersion revision) {
    final Set<Long> aGroups = revision.getValue(Group.AVAILABLE_GROUPS);
    if(aGroups == null || aGroups.isEmpty()) {
      return BugGroupInfo.GROUPS_UNKNOWN;
    }

    final BugGroupInfo groupInfo = new BugGroupInfo();
    final Set<Long> cGroups = revision.getValue(Group.CONTAINING_GROUPS);

    for(final long group : aGroups) {
      final ItemVersion v = revision.forItem(group);
      final String id = v.getValue(Group.FORM_ID);
      final String description = v.getValue(Group.DESCRIPTION);
      if(id == null || description == null) {
        assert false : group + " " + id + " " + description;
        continue;
      }
      groupInfo.addGroup(id, group, description, cGroups != null && cGroups.contains(group));
    }
    groupInfo.freeze();

    return groupInfo;
  }

  public void addChanges(UserChanges changes, ModelKey<BugGroupInfo> modelKey) {
    final BugGroupInfo value = changes.getNewValue(modelKey);
    if(value == BugGroupInfo.GROUPS_UNKNOWN) {
      return;
    }

    final Map<Long, Boolean> map = value.getAllForDatabase();
    if(map != null && !map.isEmpty()) {
      final Set<Long> aGroups = Collections15.hashSet();
      final Set<Long> cGroups = Collections15.hashSet();
      for(final Map.Entry<Long, Boolean> entry : map.entrySet()) {
        aGroups.add(entry.getKey());
        if(Boolean.TRUE.equals(entry.getValue())) {
          cGroups.add(entry.getKey());
        }
      }
      changes.getCreator().setValue(Group.AVAILABLE_GROUPS, aGroups);
      changes.getCreator().setValue(Group.CONTAINING_GROUPS, cGroups);
    }
  }

  public <SM> SM getModel(Lifespan life, ModelMap model, ModelKey<BugGroupInfo> modelKey, Class<SM> aClass) {
    assert false : aClass;
    return null;
  }
}
