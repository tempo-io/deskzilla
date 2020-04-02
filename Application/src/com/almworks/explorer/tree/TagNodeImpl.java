package com.almworks.explorer.tree;

import com.almworks.api.application.*;
import com.almworks.api.application.tree.*;
import com.almworks.api.config.ConfigNames;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.tags.TagsComponent;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.LongSet;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.ModelAware;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.*;
import com.almworks.util.ui.actions.ActionContext;
import com.almworks.util.ui.actions.CantPerformException;
import com.almworks.util.ui.actions.dnd.DragContext;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;

import static org.almworks.util.Collections15.hashSet;

class TagNodeImpl extends GenericNodeImpl implements TagNode, ModelAware {
  private final TagQueryResult myResult = new TagQueryResult(this);
  private boolean myUpdatingPresentation;

  public TagNodeImpl(String name, String iconPath, Configuration config) {
    this(new MyPresentation(name, iconPath), config);
  }

  protected TagNodeImpl(MyPresentation presentation, Configuration configuration) {
    super(presentation, configuration);
    getPresentation().setNode(this);
    addAllowedChildType(TreeNodeFactory.NodeType.FOLDER);
    addAllowedChildType(TreeNodeFactory.NodeType.QUERY);
    addAllowedChildType(TreeNodeFactory.NodeType.DISTRIBUTION_FOLDER);
    // todo merge with query
    myResult.addChangeListener(Lifespan.FOREVER, new ChangeListener() {
      public void onChange() {
        invalidatePreviewSafe();
      }
    });
  }

  public boolean isNarrowing() {
    return true;
  }

  @ThreadAWT
  public AggregatingEditCommit setOrClearTag(final Collection<? extends UiItem> items, final boolean set, AggregatingEditCommit commit) {
    Threads.assertAWTThread();
    if (items.isEmpty())
      return commit;
    final DBIdentifiedObject tag = myResult.getTagObj();
    final LongList targets = LongSet.collect(UiItem.GET_ITEM, items);
    commit.addProcedure(ThreadGate.AWT, new EditCommit() {
      @Override
      public void performCommit(EditDrain db) throws DBOperationCancelledException {
        for (ItemVersionCreator creator : db.changeItems(targets)) {
          Set<Long> value = hashSet(creator.getValue(TagsComponent.TAGS));
          long tagItem = db.materialize(tag);
          boolean taggedAlready = value.contains(tagItem);
          if (set == taggedAlready)
            continue;
          creator.setValue(TagsComponent.TAGS, changeValue(value, tagItem, set));
        }
      }

      @Override
      public void onCommitFinished(boolean success) {
        if (!success) return;
        invalidatePreview();
        maybeSchedulePreview();
      }
    });
    return commit;
  }

  private static Set<Long> changeValue(Set<Long> value, Long tag, boolean add) {
    if (add)
      value.add(tag);
    else
      value.remove(tag);
    return value;
  }

  public boolean isEditable() {
    return true;
  }

  public boolean editNode(ActionContext context) throws CantPerformException {
    return TagEditor.editNode(this, context);
  }

  public boolean isSynchronized() {
    return true;
  }

  @NotNull
  public QueryResult getQueryResult() {
    return myResult;
  }

  public boolean isCopiable() {
    return false;
  }

  public boolean isRenamable() {
    return true;
  }

  public MyPresentation getPresentation() {
    return (MyPresentation) super.getPresentation();
  }

  protected void onFirstInsertToModel() {
    super.onFirstInsertToModel();
    RootNode root = getRoot();
    assert root != null;
    myResult.initialize();
  }

  public void acceptItems(List<ItemWrapper> wrappers, DragContext context) throws CantPerformException {
    SyncManager sm = context.getSourceObject(SyncManager.ROLE);
    sm.commitEdit(setOrClearTag(wrappers, true, new AggregatingEditCommit()));
  }

  public boolean canAcceptItems(List<ItemWrapper> wrappers, DragContext context) throws CantPerformException {
    context.getSourceObject(SyncManager.ROLE);
    return true;
  }


  @ThreadAWT
  protected boolean isPreviewAvailable() {
    return myResult.getDbFilter() != null;
  }

  @CanBlock
  @Nullable
  protected ItemsPreview calculatePreview(Lifespan lifespan, DBReader reader) {
    DBFilter view = myResult.getDbFilter();
    if (view == null)
      return new ItemsPreview.Unavailable();
    if (lifespan.isEnded())
      return null;
    ItemsPreview result = CountPreview.scanView(view, lifespan, reader);
    return result;
  }

  public int removeFromTree() {
    int result = super.removeFromTree();
    myResult.delete();
    return result;
  }

  public String getIconPath() {
    return getPresentation().getIconPath();
  }

  @Override
  public Icon getIcon() {
    return getPresentation().getOpenIcon();
  }

  @Override
  public DBIdentifiedObject getTagDbObj() {
    return myResult.getTagObj();
  }

  @Override
  @ThreadAWT
  public long getTagItem() {
    return myResult.getTagItem();
  }

  public void setName(String name) {
    getPresentation().setText(name);
  }

  public void setIconPath(String iconPath) {
    getPresentation().setIconPath(iconPath);
  }

  public void updatePresentation(@Nullable String name, @Nullable String iconPath) {
    myUpdatingPresentation = true;
    try {
      MyPresentation presentation = getPresentation();
      if (name != null && name.length() > 0) {
        presentation.setText(name);
      }
      if (iconPath != null) {
        presentation.setIconPath(iconPath);
      }
    } finally {
      myUpdatingPresentation = false;
    }
    myResult.updateTag();
  }


  protected static class MyPresentation extends CounterPresentation {
    private String myIconPath;

    public MyPresentation(String name, String iconPath) {
      super(name, null, null);
      myIconPath = iconPath;
      setIcon(TagsUtil.getTagIcon(iconPath, true));
    }

    private boolean setIconPath(String iconPath) {
      if (!Util.equals(iconPath, myIconPath)) {
        myIconPath = iconPath;
        setIcon(TagsUtil.getTagIcon(iconPath, true));
        myNode.getConfiguration().setSetting(ConfigNames.ICON_PATH, Util.NN(iconPath));
        maybeUpdateTag();
        return true;
      } else {
        return false;
      }
    }

    public String getIconPath() {
      return myIconPath;
    }

    public void renderOn(Canvas canvas, CellState state) {
      super.renderOn(canvas, state);
    }

    protected boolean isSynced() {
      return true;
    }

    public boolean setText(String text) {
      boolean changed = super.setText(text);
      if (changed) {
        maybeUpdateTag();
      }
      return changed;
    }

    private void maybeUpdateTag() {
      TagNodeImpl node = (TagNodeImpl) myNode;
      if (!node.myUpdatingPresentation) {
        // otherwise updateTag will be called from there
        node.myResult.updateTag();
      }
    }
  }
}
