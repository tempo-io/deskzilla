package com.almworks.tags;

import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.engine.Engine;
import com.almworks.items.api.*;
import com.almworks.items.util.DBNamespace;
import com.almworks.util.properties.Role;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.Set;

public interface TagsComponent {
  Role<TagsComponent> ROLE = Role.role("FC");

  DBNamespace NS = Engine.NS.subModule("tags");

  /**
   * Attribute that is set on a taggable item, contains item's tags.
   */
  DBAttribute<Set<Long>> TAGS = NS.linkSet("tags", "Tags", false);

  DBNamespace TAG_NS = NS.subNs("tag");

  /**
   * Type of a tag item.
   */
  DBItemType TYPE_TAG = TAG_NS.type();

  /**
   * Attribute of tag item.
   */
  DBAttribute<String> ICON_PATH = TAG_NS.string("iconPath", "Icon Path", false);

  @NotNull
  List<ResolvedTag> loadTags(long item, DBReader reader);

  String getDislayableName();

  ConstraintDescriptor getDescriptor();
}
