package com.almworks.api.application;

import com.almworks.items.sync.ItemVersion;
import com.almworks.tags.ResolvedTag;
import com.almworks.tags.TagsComponent;
import com.almworks.util.Terms;
import com.almworks.util.commons.Function;
import com.almworks.util.exec.Context;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.TextUtil;
import org.almworks.util.Collections15;

import javax.swing.*;
import java.util.*;

public class TagsModelKey extends SystemKey<List<ResolvedTag>> {
  public static final TagsModelKey INSTANCE = new TagsModelKey();

  private final Map<String, StateIcon> myStateIconCache = Collections15.hashMap();

  private static final Function<LoadedItem, String> TOOLTIP_GETTER = new Function<LoadedItem, String>() {
    public String invoke(LoadedItem argument) {
      List<ResolvedTag> tags = argument.getModelKeyValue(INSTANCE);
      return TextUtil.separate(tags, ", ", ItemKey.DISPLAY_NAME);
    }
  };
  public static final List<ResolvedTag> NO_TAGS = Collections15.<ResolvedTag>emptyList();

  public TagsModelKey() {
    super("$tags");
  }

  public String getDisplayableName() {
    TagsComponent tags = Context.require(TagsComponent.class);
    return tags.getDislayableName();
  }

  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    TagsComponent tagsComponent = Context.get(TagsComponent.class);
    if (tagsComponent == null) return;

    List<ResolvedTag> tags = tagsComponent.loadTags(itemVersion.getItem(), itemVersion.getReader());
    if (!tags.isEmpty()) {
      Set<String> addedIcons = Collections15.hashSet();
      for (ResolvedTag tag : tags) {
        String iconPath = tag.getIconPath();
        if (!TagsUtil.NO_ICON.equals(iconPath) && addedIcons.add(iconPath)) {
          StateIconHelper.addStateIcon(values, getStateIcon(iconPath, tag.getIcon()));
        }
      }
      setValue(values, tags);
    } else {
      setValue(values, NO_TAGS);
    }
  }

  private synchronized StateIcon getStateIcon(String iconPath, Icon icon) {
    StateIcon stateIcon = myStateIconCache.get(iconPath);
    if (stateIcon == null) {
      stateIcon = new StateIcon(icon, -10, Terms.ref_Artifact + " is tagged", TOOLTIP_GETTER);
      myStateIconCache.put(iconPath, stateIcon);
    }
    return stateIcon;
  }

  public void setValue(PropertyMap values, List<ResolvedTag> value) {
    values.put(getModelKey(), value);
  }

  public void addChanges(UserChanges changes) {
    TagsComponent tagsComponent = Context.get(TagsComponent.class);
    if (tagsComponent == null)
      return;
    List<ResolvedTag> tags = changes.getNewValue(this);
    Set<Long> ptrs = null;
    if (tags != null && !tags.isEmpty()) {
      ptrs = Collections15.hashSet();
      for (ResolvedTag tag : tags) {
        long a = tag.getResolvedItem();
        if(a != ItemKey.NOT_RESOLVED_LONG) {
          ptrs.add(a);
        }
      }
    }
    changes.getCreator().setValue(TagsComponent.TAGS, ptrs == null || ptrs.isEmpty() ? null : ptrs);
  }
}
