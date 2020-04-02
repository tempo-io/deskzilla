package com.almworks.bugzilla.provider.datalink.schema;

import com.almworks.bugzilla.integration.BugzillaHTMLConstants;
import com.almworks.bugzilla.integration.data.BugGroupData;
import com.almworks.bugzilla.provider.BugzillaProvider;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.sync.DBDrain;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.items.util.*;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.TypedKey;

import java.util.Map;
import java.util.Set;

public class Group {
  public static final DBNamespace NS = BugzillaProvider.NS.subNs("group");
  public static final DBItemType type = NS.type();
  public static final DBAttribute<String> NAME = NS.string("name", "Name", false);
  public static final DBAttribute<String> DESCRIPTION = NS.string("description", "Description", false);
  public static final DBAttribute<String> FORM_ID = NS.string("formId", "Form ID", false);

  /**
   * Applicable to product and bug
   */
  public static final DBAttribute<Set<Long>> AVAILABLE_GROUPS = NS.linkSet("groups", "available_groups", false);
  public static final DBAttribute<Set<Long>> CONTAINING_GROUPS = NS.linkSet("containingGroups", "containing_groups", true);

  private static final TypedKey<Map<String, Long>> CACHE_KEY = TypedKey.create("groups");

  public static long getGroupItem(BugGroupData groupInfo, PrivateMetadata privateMetadata, DBDrain drain) {
    String formId = groupInfo.getFormId();

    final DBReader reader = drain.getReader();
    final Map<String, Long> cache = DatabaseUtil.getMapCache(reader, CACHE_KEY);
    final Long known = cache.get(formId);
    if(known != null) {
      return known;
    }

    final DBQuery query = reader.query(getGroupsExpr(privateMetadata));
    long group = query.getItemByKey(FORM_ID, formId);
    if(group <= 0) {
      final String altFormId = BugzillaHTMLConstants.getAlternativeFormId(formId);
      if(altFormId != null) {
        group = query.getItemByKey(FORM_ID, altFormId);
      }
    }

    final ItemVersionCreator creator;
    if(group <= 0) {
      creator = drain.createItem();
      creator.setValue(SyncAttributes.CONNECTION, privateMetadata.thisConnection);
      creator.setValue(DBAttribute.TYPE, type);
    } else {
      creator = drain.changeItem(group);
    }

    creator.setAlive();
    creator.setValue(FORM_ID, formId);
    creator.setValue(NAME, groupInfo.getName());
    creator.setValue(DESCRIPTION, groupInfo.getDescription());

    cache.put(formId, creator.getItem());
    return creator.getItem();
  }

  private static BoolExpr<DP> getGroupsExpr(PrivateMetadata privateMetadata) {
    return BoolExpr.and(
      DPEqualsIdentified.create(DBAttribute.TYPE, type),
      DPEqualsIdentified.create(SyncAttributes.CONNECTION, privateMetadata.thisConnection));
  }
  
}
