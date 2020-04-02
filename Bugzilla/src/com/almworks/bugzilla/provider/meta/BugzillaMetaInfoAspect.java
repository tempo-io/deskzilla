package com.almworks.bugzilla.provider.meta;

import com.almworks.api.actions.EditWatchersAction;
import com.almworks.api.actions.StdItemActions;
import com.almworks.api.application.*;
import com.almworks.api.application.viewer.textdecorator.TextDecoratorRegistry;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.Connection;
import com.almworks.api.engine.Engine;
import com.almworks.api.explorer.gui.OrderListModelKey;
import com.almworks.bugzilla.gui.flags.edit.EditFlagsAction;
import com.almworks.bugzilla.gui.votes.ChangeVoteAction;
import com.almworks.bugzilla.gui.votes.ToggleVoteAction;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.timetrack.gui.TimeTrackingActions;
import com.almworks.util.advmodel.*;
import com.almworks.util.commons.*;
import com.almworks.util.components.TreeModelBridge;
import com.almworks.util.components.TreeStructure;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.images.Icons;
import com.almworks.util.models.TableColumnAccessor;
import com.almworks.util.ui.actions.AnAction;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author dyoma
 */
public class BugzillaMetaInfoAspect {
  private final ComponentContainer myContainer;
  private final Configuration myConfig;
  private final OrderListModel<TableColumnAccessor<LoadedItem, ?>> myFieldColumns = OrderListModel.create();

  private BugzillaMetaInfo myMetaInfo;

  public BugzillaMetaInfoAspect(ComponentContainer container, Configuration config) {
    myContainer = container;
    myConfig = config;
  }

  public MetaInfo buildMetaInfo() {
    synchronized (this) {
      assert myMetaInfo == null;
      myMetaInfo = createMetaInfo();
      return myMetaInfo;
    }
  }

  private BugzillaMetaInfo createMetaInfo() {
    final List<AnAction> actions = Collections15.arrayList();
    final Map<TypedKey<AnAction>, AnAction> customActions = new HashMap<TypedKey<AnAction>, AnAction>();
    createActions(actions, customActions, myContainer.requireActor(Engine.ROLE));

    setFieldColumns();

    final AListModel<ItemsTreeLayout> treeLayouts = createTreeLayouts(BugzillaKeys.depends, BugzillaKeys.blocks);

    final TextDecoratorRegistry decorators = myContainer.requireActor(TextDecoratorRegistry.ROLE);
    decorators.addParser(new BugReferenceParser());

    return new BugzillaMetaInfo(actions, treeLayouts, decorators, customActions, myConfig.getOrCreateSubset("info"));
  }

  private void setFieldColumns() {
    final List<TableColumnAccessor<LoadedItem, ?>> list = BugzillaKeys.createColumns().getColumns();
    ThreadGate.AWT.execute(new Runnable() {
      public void run() {
        myFieldColumns.setElements(list);
      }
    });
  }

  private static void createActions(List<AnAction> actions, Map<TypedKey<AnAction>, AnAction> customActions, Engine engine) {
    RemoveBugForSyncProblemAction deleteBugForSyncProblemAction = new RemoveBugForSyncProblemAction(BugzillaKeys.id, engine);
    BugzillaMetaInfo.DELETE_BUG_ACTION.putTo(customActions, deleteBugForSyncProblemAction);
    actions.add(deleteBugForSyncProblemAction);

    actions.add(StdItemActions.ATTACH_FILE);
    actions.add(StdItemActions.ATTACH_SCREENSHOT);

    final Function<ItemWrapper, ItemKey> meGetter = new Function<ItemWrapper, ItemKey>() {
      public ItemKey invoke(ItemWrapper argument) {

        Connection connection = argument.getConnection();
        if (connection == null)
          return null;
        BugzillaContext context = connection.getConnectionContainer().getActor(BugzillaContext.ROLE);
        if (context == null)
          return null;
        return BugzillaKeys.cc.getResolver().getItemKey(context.getConfiguration().getValue().getUsername());
      }
    };

    actions.add(TimeTrackingActions.START_WORK);
    actions.add(TimeTrackingActions.STOP_WORK);

    final ToggleVoteAction toggleVoteAction = new ToggleVoteAction();
    BugzillaMetaInfo.TOGGLE_VOTE_ACTION.putTo(customActions, toggleVoteAction);
    actions.add(toggleVoteAction);

    final ChangeVoteAction changeVoteAction = new ChangeVoteAction(BugzillaKeys.voteKeys, BugzillaKeys.id);
    BugzillaMetaInfo.CHANGE_VOTE_ACTION.putTo(customActions, changeVoteAction);
    actions.add(changeVoteAction);

    final Factory<BaseEnumConstraintDescriptor> descriptorFactory = new Lazy<BaseEnumConstraintDescriptor>() {
      @NotNull
      protected BaseEnumConstraintDescriptor instantiate() {
        return CommonMetadata.getEnumDescriptor(BugzillaAttribute.ASSIGNED_TO);
      }
    };

    EditWatchersAction editWatchersAction = new EditWatchersAction(
      BugzillaKeys.cc, null, null, null, meGetter, descriptorFactory,
      User.userResolver, BugzillaKeys.id, "CC list", Icons.WATCH_ACTION);
    BugzillaMetaInfo.EDIT_CC_LIST_ACTION.putTo(customActions, editWatchersAction);
    actions.add(editWatchersAction);
    actions.add(EditFlagsAction.INSTANCE);
  }

  private static AListModel<ItemsTreeLayout> createTreeLayouts(OrderListModelKey<ItemKey, Set<Long>> depends, OrderListModelKey<ItemKey, Set<Long>> blocks) {
    ItemsTreeLayout dependsLayout = new BugTreeLayout("depends", new BlocksDependsStructure(blocks), "BugzillaTreeLayout.depends");
    ItemsTreeLayout blocksLayout = new BugTreeLayout("blocks", new BlocksDependsStructure(depends), "BugzillaTreeLayout.blocks");
    return FixedListModel.create(dependsLayout, blocksLayout);
  }

  @NotNull
  public AListModel<TableColumnAccessor<LoadedItem, ?>> getFieldColumns() {
    return myFieldColumns;
  }

  private static class BlocksDependsStructure
    implements TreeStructure.MultiParent<LoadedItem, Long, TreeModelBridge<LoadedItem>>
  {
    private final OrderListModelKey<ItemKey, Set<Long>> myOppositeKey;

    public BlocksDependsStructure(OrderListModelKey<ItemKey, Set<Long>> oppositeKey) {
      myOppositeKey = oppositeKey;
    }

    public Long getNodeKey(LoadedItem element) {
      return element.getItem();
    }

    public Long getNodeParentKey(LoadedItem element) {
      Log.warn("BlocksDependsStructure: shouldn't be here.");
      Collection<ItemKey> value = myOppositeKey.getValue(element.getValues());
      if (value == null || value.size() != 1)
        return null;
      ItemKey parent = value.iterator().next();
      if (parent == null) {
        assert false : element + " " + myOppositeKey;
        return null;
      }
      return parent.getResolvedItem();
    }

    @Override
    public Set<Long> getNodeParentKeys(LoadedItem element) {
      final Collection<ItemKey> value = myOppositeKey.getValue(element.getValues());
      if(value == null || value.isEmpty()) {
        return Collections.emptySet();
      }
      
      final Set<Long> result = Collections15.hashSet();
      for(final ItemKey itemKey : value) {
        if(itemKey == null) {
          assert false : element + " " + myOppositeKey;
          continue;
        }
        final long itemId = itemKey.getResolvedItem();
        if(itemId <= 0L) {
          assert false : element + " " + myOppositeKey;
          continue;
        }
        result.add(itemId);
      }

      return result.isEmpty() ? Collections.<Long>emptySet() : result;
    }

    public TreeModelBridge<LoadedItem> createTreeNode(LoadedItem element) {
      return TreeModelBridge.create(element);
    }
  }

  private static class BugTreeLayout extends ItemsTreeLayout {
    public BugTreeLayout(String displayName, TreeStructure<LoadedItem, ?, TreeModelBridge<LoadedItem>> treeStructure,
      String id)
    {
      super(displayName, treeStructure, id);
    }

    public boolean isApplicableTo(Connection connection) {
      return connection instanceof BugzillaConnection;
    }
  }
}
