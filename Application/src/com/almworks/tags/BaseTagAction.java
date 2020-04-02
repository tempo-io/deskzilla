package com.almworks.tags;

import com.almworks.api.actions.ItemActionUtils;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.TagsModelKey;
import com.almworks.api.application.tree.TagNode;
import com.almworks.items.sync.SyncManager;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.util.ui.actions.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.List;

public abstract class BaseTagAction extends SimpleAction {
  private final boolean myAdd;

  public BaseTagAction(boolean add, @Nullable String name, @Nullable Icon icon) {
    super(name, icon);
    myAdd = add;
  }

  protected boolean isAdd() {
    return myAdd;
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    List<ItemWrapper> wrappers = ItemActionUtils.basicUpdate(context);
    wrappers = CantPerformException.ensureNotEmpty(wrappers);
    List<TagNode> tags = getTargetCollections(context);
    boolean applicable = false;
    for (TagNode tagNode : tags) {
      applicable = isApplicable(tagNode, wrappers, isAdd());
      if (applicable)
        break;
    }
    context.setEnabled(applicable);
  }

  protected abstract List<TagNode> getTargetCollections(ActionContext context) throws CantPerformException;

  protected static boolean isApplicable(TagNode tagNode, List<ItemWrapper> items, boolean add) {
    for (ItemWrapper item : items) {
      List<ResolvedTag> value = TagsModelKey.INSTANCE.getValue(item.getLastDBValues());
      if (value == null) {
        // strange
        continue;
      }
      boolean belongs = belongsTo(tagNode, value);
      if (add && !belongs)
        return true;
      if (!add && belongs)
        return true;
    }
    return false;
  }

  protected void applyChange(final List<ItemWrapper> wrappers, final List<TagNode> nodes, ActionContext context) throws CantPerformException {
    AggregatingEditCommit commit = new AggregatingEditCommit();
    for (TagNode tag : nodes) {
      tag.setOrClearTag(wrappers, isAdd(), commit);
    }
    context.getSourceObject(SyncManager.ROLE).commitEdit(commit);
  }

  public static boolean belongsTo(TagNode tagNode, List<ResolvedTag> list) {
    long tagItem = tagNode.getTagItem();
    for (ResolvedTag tag : list) {
      if (tag.getResolvedItem() == tagItem)
        return true;
    }
    return false;
  }
}
