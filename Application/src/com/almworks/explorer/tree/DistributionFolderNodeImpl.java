package com.almworks.explorer.tree;

import com.almworks.api.application.*;
import com.almworks.api.application.qb.*;
import com.almworks.api.application.tree.*;
import com.almworks.api.config.ConfigNames;
import com.almworks.api.explorer.util.ItemKeys;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.explorer.qbuilder.filter.EnumConstraintKind;
import com.almworks.integers.IntArray;
import com.almworks.util.English;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.collections.*;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.config.*;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.images.Icons;
import com.almworks.util.sfs.StringFilterSet;
import com.almworks.util.threads.*;
import com.almworks.util.ui.ColorUtil;
import org.almworks.util.*;
import org.almworks.util.detach.*;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class DistributionFolderNodeImpl extends GenericNodeImpl implements DistributionFolderNode, RenamableNode, ModelAware {
  public static final Comparator<DistributionQueryNode> DISTRIBUTION_QUERY_ORDER = new DistributionComparator();
  public static final Comparator<DistributionGroupNode> DISTRIBUTION_GROUP_ORDER = new DistributionGroupComparator();

  protected static final BottleneckJobs<DistributionFolderNodeImpl> UPDATE_HYPERCUBE =
    new BottleneckJobs<DistributionFolderNodeImpl>(500, ThreadGate.STRAIGHT) {
      protected void execute(DistributionFolderNodeImpl job) {
        job.onHypercubeUpdated();
      }
    };

  protected static final BottleneckJobs<DistributionFolderNodeImpl> PROCESS_MODEL_UPDATES =
    new BottleneckJobs<DistributionFolderNodeImpl>(500, ThreadGate.STRAIGHT) {
      protected void execute(DistributionFolderNodeImpl job) {
        job.processPendingUpdates();
      }
    };

  private static final DistributionVisitor QUERIES_UPDATER = new DistributionVisitor() {
    @SuppressWarnings({"RefusedBequest"})
    protected Object visitQuery(DistributionQueryNodeImpl query, Object ticket) {
      query.updateName();
      query.updateGroup();
      return ticket;
    }
  };
  private static final DistributionVisitor EMPTY_GROUPS_REMOVER = new DistributionVisitor() {
    protected Object collectQuery(DistributionQueryNodeImpl query, Object ticket, List list) {
      return ticket;
    }

    protected Object visitGroup(DistributionGroupNodeImpl group, Object ticket) {
      if (group.getChildrenCount() == 0)
        group.removeFromTree();
      return ticket;
    }
  };

  private final ParentResult myResult = new ParentResult(this);
  private ItemHypercube myLastCube = null;

  private final NodeParams myParams;

  static final String REMOVED_PREFIX = "Removed: ";

  private final Lifecycle myLife = new Lifecycle();
  private final Lifecycle myItemKeyModelLife = new Lifecycle();

  private boolean myExpandAfterNextUpdate = false;

  // temporary sets for processing updates
  private Set<ItemKey> myPendingAdded;
  private Set<ItemKey> myPendingRemoved;
  private Set<ItemKey> myPendingChanged;
  private boolean myPendingProcessingRequested;

  private boolean myShowExpandingNodeWhenInserted;
  private ExpandingProgressNode myExpandingProgressNode;
  private final DistributionChildrenUpdate myChildrenUpdate = new DistributionChildrenUpdate(this);
  private final HidingEmptyChildren myHidingChildren = new HidingEmptyChildren(this);

  private static final BottleneckJobs<DistributionFolderNodeImpl> UPDATE_ALL_GROUPS =
    new BottleneckJobs<DistributionFolderNodeImpl>(500, ThreadGate.AWT) {
      protected void execute(DistributionFolderNodeImpl job) {
        job.updateAllQueryGroups();
      }
    };

  public DistributionFolderNodeImpl(String name, Configuration configuration) {
    super(new EditableText(name, Icons.NODE_DISTRIBUTION_FOLDER_OPEN), configuration);
    setPresentation(
      new HidingPresentation(
        name, Icons.NODE_DISTRIBUTION_FOLDER_OPEN,
        Icons.NODE_DISTRIBUTION_FOLDER_HIDING, this));

    addAllowedChildType(TreeNodeFactory.NodeType.DISTRIBUTION_QUERY);
    addAllowedChildType(TreeNodeFactory.NodeType.DISTRIBUTION_GROUP);
    beRemovable();
    myParams = new NodeParams(this);
    myParams.readConfig(configuration);
  }

  @ThreadAWT
  public void setParameters(@Nullable ConstraintDescriptor descriptor, @NotNull DistributionParameters parameters) {
    if (parameters == null) {
      assert false : this;
      return;
    }
    myLastCube = null; // force update
    myParams.setParameters(descriptor, parameters);
    fireSubtreeChanged();
    UPDATE_ALL_GROUPS.addJobDelayed(this);
    UPDATE_HYPERCUBE.addJobDelayed(this);

    if (descriptor != null) {
      String attributeName = descriptor.getDisplayName();
      String name = English.capitalizeWords(attributeName);
      if (StringFilterSet.isFiltering(parameters.getGroupsFilter()) ||
        StringFilterSet.isFiltering(parameters.getValuesFilter()))
        name += " (filtering)";
      getPresentation().setText(name);
    }
  }

  public ConstraintDescriptor getDescriptor() {
    return myParams.getDescriptor();
  }

  @NotNull
  public DistributionParameters getParameters() {
    return myParams.getParameters();
  }

  @NotNull
  public QueryResult getQueryResult() {
    return myResult;
  }

  public boolean isCopiable() {
    return true;
  }

  public boolean isRenamable() {
    return true;
  }

  public EditableText getPresentation() {
    return (EditableText) super.getPresentation();
  }

  public void onInsertToModel() {
    super.onInsertToModel();
    if (myShowExpandingNodeWhenInserted) {
      myShowExpandingNodeWhenInserted = false;
      if (getChildrenCount() == 0) {
        myExpandingProgressNode = new ExpandingProgressNode(getConfiguration());
        addChildNode(myExpandingProgressNode);
      }
    }
    updateAndListenParent();
  }

  private void updateAndListenParent() {
    GenericNode parent = getParent();
    if (parent == null) {
      assert false : this;
      return;
    }
    ChangeListener onParentChanged = new ChangeListener() {
      public void onChange() {
        myParams.resolveAttribute();
        UPDATE_HYPERCUBE.addJobDelayed(DistributionFolderNodeImpl.this);
      }
    };
    parent.getQueryResult().addAWTChangeListener(myLife.lifespan(), onParentChanged);
    onParentChanged.onChange();
  }

  public void onRemoveFromModel() {
    myLife.cycle();
    myItemKeyModelLife.cycle();
    super.onRemoveFromModel();
  }

  @SuppressWarnings({"MethodOnlyUsedFromInnerClass"})
  private void onHypercubeUpdated() {
    if (!isNode())
      return;
    ItemHypercube cube = getHypercube(false);
    if (!same(myLastCube, cube)) {
      myLastCube = cube;
      if (myLastCube != null) {
        ConstraintType type = myParams.getConstraintType();
        if (type instanceof EnumConstraintType) {
          onModelUpdated((EnumConstraintType) type, cube);
        } else {
          onModelUpdated(null, cube);
        }
      }
    }
    ExpandingProgressNode progressNode = myExpandingProgressNode;
    if (progressNode != null) {
      GenericNode parent = progressNode.getParent();
      if (parent == this) {
        myExpandingProgressNode = null;
        boolean wasSelected = false;
        RootNode root = getRoot();
        if (root != null) {
          wasSelected = root.getNodeFactory().getTree().getSelectionAccessor().isSelected(progressNode.getTreeNode());
        }
        progressNode.removeFromTree();
        if (wasSelected) {
          root.getNodeFactory().selectNode(this, false);
        }
      }
    }
  }

  private boolean same(ItemHypercube cube1, ItemHypercube cube2) {
    if (cube1 == null)
      return cube2 == null;
    else
      return cube1.isSame(cube2);
  }

  private void onModelUpdated(@Nullable EnumConstraintType type, ItemHypercube cube) {
    myItemKeyModelLife.cycle();
    final Lifespan lifespan = myItemKeyModelLife.lifespan();
    final AListModel<ItemKey> model = type == null ? AListModel.EMPTY : type.getEnumModel(lifespan, cube);
    List<ItemKey> options = model.toList();
    fullUpdate(options);
    if (myExpandAfterNextUpdate) {
      expandNode();
      myExpandAfterNextUpdate = false;
    }
    // As we've just performed full update, we should forget old incremental updates (applying them would lead to bugs)
    clearPendingModelUpdates();
    listenModel(lifespan, model);
  }

  private void clearPendingModelUpdates() {
    myPendingAdded = null;
    myPendingChanged = null;
    myPendingRemoved = null;
    myPendingProcessingRequested = false;
    PROCESS_MODEL_UPDATES.removeJob(this);
  }

  private void listenModel(final Lifespan lifespan, final AListModel<ItemKey> model) {
    lifespan.add(model.addListener(new AListModel.Adapter() {
      public void onInsert(int index, int length) {
        if (lifespan.isEnded())
          return;
        Threads.assertAWTThread();
        if (length > 0) {
          pendingAdd(model.subList(index, index + length));
        }
      }

      public void onItemsUpdated(AListModel.UpdateEvent event) {
        if (lifespan.isEnded())
          return;
        Threads.assertAWTThread();
        int length = event.getAffectedLength();
        if (length > 0) {
          int index = event.getLowAffectedIndex();
          pendingChange(model.subList(index, index + length));
        }
      }
    }));
    lifespan.add(model.addRemovedElementListener(new AListModel.RemovedElementsListener<ItemKey>() {
      public void onBeforeElementsRemoved(AListModel.RemoveNotice<ItemKey> elements) {
        if (lifespan.isEnded())
          return;
        Threads.assertAWTThread();
        pendingRemove(elements.getList());
      }
    }));
  }

  private void pendingChange(List<ItemKey> keys) {
    Threads.assertAWTThread();
    for (ItemKey key : keys) {
      if (myPendingAdded != null && myPendingAdded.contains(key)) {
        // do nothing since it will already be updated
      } else if (myPendingRemoved != null && myPendingRemoved.contains(key)) {
        // do nothing since it will be removed anyway
      } else {
        if (myPendingChanged == null)
          myPendingChanged = Collections15.hashSet();
        myPendingChanged.add(key);
      }
    }
    requestProcessing();
  }

  private void pendingAdd(List<ItemKey> keys) {
    Threads.assertAWTThread();
    for (ItemKey key : keys) {
      if (myPendingChanged != null && myPendingChanged.contains(key)) {
        // do nothing since it has already changed
      } else if (myPendingRemoved != null && myPendingRemoved.remove(key)) {
        // key was just removed, and added back - so it's changed
        if (myPendingChanged == null)
          myPendingChanged = Collections15.hashSet();
        myPendingChanged.add(key);
      } else {
        // key is inserted
        if (myPendingAdded == null)
          myPendingAdded = Collections15.hashSet();
        myPendingAdded.add(key);
      }
    }
    requestProcessing();
  }

  private void pendingRemove(List<ItemKey> keys) {
    Threads.assertAWTThread();
    if (myPendingChanged != null)
      myPendingChanged.removeAll(keys);
    if (myPendingAdded != null)
      myPendingAdded.removeAll(keys);
    if (myPendingRemoved == null)
      myPendingRemoved = Collections15.hashSet();
    myPendingRemoved.addAll(keys);
    requestProcessing();
  }

  private void requestProcessing() {
    Threads.assertAWTThread();
    if (myPendingProcessingRequested)
      return;
    myPendingProcessingRequested = true;
    // let events accumulate and possibly negate each other
    PROCESS_MODEL_UPDATES.addJobDelayed(this);
  }

  private void processPendingUpdates() {
    Threads.assertAWTThread();
    if (!myPendingProcessingRequested) {
      // Processing request was cancelled, nothing to do
      return;
    }
    myPendingProcessingRequested = false;
    if (myPendingRemoved != null) {
      removeValues(myPendingRemoved);
      myPendingRemoved.clear();
      myPendingRemoved = null;
    }
    if (myPendingAdded != null) {
      addAcceptedValues(myPendingAdded);
      myPendingAdded.clear();
      myPendingAdded = null;
    }
    if (myPendingChanged != null) {
      updateValues(myPendingChanged);
      myPendingChanged.clear();
      myPendingChanged = null;
    }
  }

  private void expandNode() {
    // kludge :(
    RootNode root = getRoot();
    if (root != null) {
      TreeNodeFactory factory = root.getNodeFactory();
      if (factory != null) {
        factory.expandNode(this);
      }
    }
  }

  private void fullUpdate(Collection<ItemKey> optionsList) {
    final Set<ItemKey> options = createAcceptedKeySet(optionsList);

    Condition<ItemKey> condition = new Condition<ItemKey>() {
      public boolean isAccepted(ItemKey value) {
        return !options.remove(value);
      }
    };

    removeValues(condition);
    updateQueries();
    addAcceptedValues(options);

    removeEmptyGroups();
  }

  private Set<ItemKey> createAcceptedKeySet(Collection<ItemKey> fullList) {
    final Set<ItemKey> options = Collections15.linkedHashSet();
    for (ItemKey key : fullList) {
      if (isItemKeyAccepted(key)) {
        options.add(key);
      }
    }
    return options;
  }

  private boolean isItemKeyAccepted(@Nullable ItemKey key) {
    if (key == null)
      return false;
    StringFilterSet valuesFilter = getParameters().getValuesFilter();
    if (StringFilterSet.isFiltering(valuesFilter)) {
      String name = getFilterConvertor().convert(key);
      if (name == null || name.trim().length() == 0)
        return false;
      assert valuesFilter != null;
      if (!valuesFilter.isAccepted(name)) {
        return false;
      }
    }
    if (StringFilterSet.isFiltering(getParameters().getGroupsFilter())) {
      EnumGrouping<?> grouping = myParams.getGrouping();
      if (grouping != null) {
        ResolvedItem resolved = QueryUtil.getOneResolvedItem(key, getDescriptor(), getHypercube(false));
        ItemKeyGroup group = resolved == null ? null : grouping.getGroup(resolved);
        if (!isItemKeyGroupAccepted(group)) {
          return false;
        }
      }
    }
    return true;
  }

  @NotNull
  private Convertor<ItemKey, String> getFilterConvertor() {
    EnumConstraintType type = Util.castNullable(EnumConstraintType.class, myParams.getConstraintType());
    if (type == null) return ItemKey.DISPLAY_NAME;
    Convertor<ItemKey, String> convertor = type.getFilterConvertor();
    if (convertor == null) convertor = ItemKey.DISPLAY_NAME;
    return convertor;
  }

  private boolean isItemKeyGroupAccepted(@Nullable ItemKeyGroup group) {
    StringFilterSet groupsFilter = getParameters().getGroupsFilter();
    if (StringFilterSet.isFiltering(groupsFilter)) {
      String groupName = group == null ? null : group.getDisplayableName();
      assert groupsFilter != null;
      return groupsFilter.isAccepted(Util.NN(groupName, ItemKeyGroup.NULL_GROUP_SENTINEL.getDisplayableName()));
    }
    return true;
  }

  private void updateQueries() {
    if (getDescriptor() != null) {
      QUERIES_UPDATER.visit(this, null);
    }
  }

  private void removeValues(final Condition<ItemKey> condition) {
    final ConstraintDescriptor ownDescriptor = getDescriptor();
    if (ownDescriptor == null)
      return;

    new DistributionVisitor() {
      protected Object visitQuery(DistributionQueryNodeImpl query, Object ticket) {
        maybeRemoveQuery(query, ownDescriptor, condition);
        return ticket;
      }
    }.visit(this, null);

    removeEmptyGroups();
  }

  private void removeEmptyGroups() {
    EMPTY_GROUPS_REMOVER.visit(this, null);
  }

  private void maybeRemoveQuery(DistributionQueryNodeImpl query, ConstraintDescriptor folderDescriptor,
    Condition<ItemKey> condition)
  {
    if (!query.isPinned())
      return;
    Pair<ConstraintDescriptor, ItemKey> pair = query.getAttributeValue();
    if (pair != null) {
      ConstraintDescriptor descriptor = pair.getFirst();
      if (folderDescriptor.equals(descriptor)) {
        ItemKey value = pair.getSecond();
        boolean removing = condition.isAccepted(value);
        if (!removing) {
          // do not
          return;
        }
      }
    }
    removeQuerySafely(query);
  }

  private void removeQuerySafely(DistributionQueryNodeImpl query) {
    query.setPinned(false);
    if (query.getChildrenCount() == 0) {
      query.removeFromTree();
    } else {
      String text = query.getPresentation().getText();
      if (!text.startsWith(REMOVED_PREFIX))
        query.getPresentation().setText(REMOVED_PREFIX + text);
      query.getTreeNode().removeFromParent();
      placeQuery(query, getItemKeyGroup(query));
    }
  }

  private void updateValues(final Collection<ItemKey> options) {
    final Map<ItemKey, ItemKey> keys = Convertors.<ItemKey>identity().assignKeys(options);

    new DistributionVisitor() {
      protected Object visitQuery(DistributionQueryNodeImpl query, Object ticket) {
        Pair<ConstraintDescriptor, ItemKey> pair = query.getAttributeValue();
        if (pair != null) {
          ItemKey key = pair.getSecond();
          ItemKey newKey = keys.remove(key);
          if (newKey != null) {
            if (isItemKeyAccepted(newKey)) {
              query.updateName();
              updateQueryGroup(query);
            } else {
              removeQuerySafely(query);
            }
          }
        }
        return ticket;
      }

      protected Object visitGroup(DistributionGroupNodeImpl group, Object ticket) {
        group.sortChildrenLater();
        return ticket;
      }
    }.visit(this, null);

    if (keys.size() > 0) {
      addAcceptedValues(keys.keySet());
    }

    removeEmptyGroups();
    sortChildrenLater();
  }

  void updateQueryGroup(DistributionQueryNodeImpl query) {
    updateQueryGroup(query, true);
  }

  private void updateQueryGroup(DistributionQueryNodeImpl query, boolean scheduleFullUpdateIfChanged) {
    GenericNode parent = query.getParent();
    assert query.isNode() && (parent == this || (parent != null && parent.getParent() == this));
    EnumGrouping<?> grouping = myParams.getGroupingForTree();
    ItemKeyGroup newGroup = null;
    if (grouping != null) {
      ResolvedItem artifact = getResolvedItemForQueryGroup(query);
      if (artifact != null) {
        newGroup = grouping.getGroup(artifact);
      }
    }
    ItemKeyGroup oldGroup = null;
    if (parent instanceof DistributionGroupNodeImpl) {
      DistributionGroupNodeImpl groupNode = (DistributionGroupNodeImpl) parent;
      oldGroup = groupNode.getGroup();
      if (oldGroup == null && newGroup != null) {
        if (groupNode.isSameGroup(newGroup)) {
          // group wasn't initialized yet
          groupNode.updateGroup(newGroup);
          sortChildrenLater();
          oldGroup = newGroup;
        }
      }
    }
    if (!Util.equals(newGroup, oldGroup)) {
      if (isItemKeyGroupAccepted(newGroup)) {
        query.getTreeNode().removeFromParent();
        placeQuery(query, newGroup);
      } else {
        removeQuerySafely(query);
      }
      if (scheduleFullUpdateIfChanged) {
        UPDATE_ALL_GROUPS.addJobDelayed(this);
      }
    }
  }

  private void updateAllQueryGroups() {
    if (!isNode())
      return;
    if (myParams.getGrouping() == null)
      return;

    new DistributionVisitor() {
      protected Object visitQuery(DistributionQueryNodeImpl query, Object ticket) {
        updateQueryGroup(query, false);
        return false;
      }
    }.visit(this, null);

    removeEmptyGroups();
  }

  private void addAcceptedValues(Collection<ItemKey> options) {
    ConstraintDescriptor descriptor = getDescriptor();
    if (descriptor == null)
      return;
    if (options.size() > 0) {
      RootNode root = getRoot();
      if (root == null) {
        assert false;
        Log.warn("no root");
        return;
      }
      TreeNodeFactory factory = root.getNodeFactory();
      if (factory == null) {
        assert false;
        Log.warn("no factory");
        return;
      }
      MultiMap<ItemKey, DistributionQueryNodeImpl> removed = collectRemovedChildren();

      MultiMap<ItemKeyGroup, DistributionQueryNodeImpl> queriesToPlace = MultiMap.create();
      for (ItemKey value : options) {
        if (!isItemKeyAccepted(value))
          continue;
        List<DistributionQueryNodeImpl> candidates = removed != null ? removed.getAll(value) : null;
        DistributionQueryNodeImpl query = null;
        if (candidates != null && candidates.size() > 0) {
          query = candidates.get(0);
          String text = query.getPresentation().getText();
          if (text.startsWith(REMOVED_PREFIX)) {
            query.setPinned(true);
            query.getPresentation().setText(text.substring(REMOVED_PREFIX.length()).trim());
            query.getTreeNode().removeFromParent();
            queriesToPlace.add(getItemKeyGroup(query), query);
          } else {
            assert false : query;
            query = null;
          }
        }
        if (query == null) {
          Configuration config = getConfiguration().createSubset(ConfigNames.KLUDGE_DISTRIBUTION_QUERY_TAG_NAME);
          query = new DistributionQueryNodeImpl("", config, Pair.create(descriptor, value));
//          query.getPresentation().setText(value.getDisplayName());
          query.setPinned(true);
          applyPrototype(query, factory);
          query.setFilter(BaseEnumConstraintDescriptor.createNode(descriptor, value));
          query.updateName();
          queriesToPlace.add(getItemKeyGroup(query), query);
        }
      }
      for (ItemKeyGroup group : queriesToPlace.keySet()) {
        // As we are inserting nodes from the end to the beginning of the children list, we want to have a list where the heaviest node is the first
        List<DistributionQueryNodeImpl> nodes = queriesToPlace.getAllEditable(group);
        if (nodes == null) continue;
        GenericNodeImpl parent = group == null ? this : findOrCreateGroup(group);
        assert parent != null;
        ItemHypercube cube = parent == null ? new ItemHypercubeImpl() : parent.getHypercube(false);
        Collections.sort(nodes, Containers.reverse(new DistributionComparator(cube)));
        int start = -1;
        for (DistributionQueryNodeImpl node : nodes) {
          start = placeQueryUnder(parent, node, start);
        }
      }
    }
  }

  @Nullable
  private MultiMap<ItemKey, DistributionQueryNodeImpl> collectRemovedChildren() {
    final ConstraintDescriptor descriptor = getDescriptor();
    if (descriptor == null)
      return null;
    return new DistributionVisitor<MultiMap<ItemKey, DistributionQueryNodeImpl>>() {
      protected MultiMap<ItemKey, DistributionQueryNodeImpl> visitQuery(DistributionQueryNodeImpl query,
        MultiMap<ItemKey, DistributionQueryNodeImpl> ticket)
      {
        if (!query.isPinned()) {
          String text = query.getPresentation().getText();
          if (text != null && text.startsWith(REMOVED_PREFIX)) {
            Pair<ConstraintDescriptor, ItemKey> pair = query.getAttributeValue();
            assert pair != null;
            if (!pair.getFirst().getId().equals(descriptor.getId()))
              Log.warn("Alive variant kept from different distribution: " + pair.getFirst().getId() + " " +
                descriptor.getId());
            ItemKey value = pair.getSecond();
            assert value != null : this;
            assert value != ItemKeyStub.ABSENT : this;
            if (ticket == null)
              ticket = MultiMap.create();
            ticket.add(value, query);
          }
        }
        return ticket;
      }
    }.visit(this, null);
  }

  public ReadonlyConfiguration createCopy(Configuration parentConfig) {
    // kludge: repetition from LazyDistributionNodeImpl
    Configuration copy = (Configuration) super.createCopy(parentConfig);
    Configuration thisConfig = getConfiguration();
    Configuration prototype = thisConfig.getSubset(ConfigNames.PROTOTYPE_TAG);
    if (!prototype.isEmpty()) {
      ConfigurationUtil.copyTo(prototype, copy.createSubset(ConfigNames.PROTOTYPE_TAG));
    }
    ConfigurationUtil.copySubsetsTo(thisConfig, copy, ConfigNames.DISTRIBUTION_VALUES_FILTER);
    ConfigurationUtil.copySubsetsTo(thisConfig, copy, ConfigNames.DISTRIBUTION_GROUPS_FILTER);
    return copy;
  }

  private void applyPrototype(DistributionQueryNode query, TreeNodeFactory factory) {
    Configuration prototype = getConfiguration().getSubset(ConfigNames.PROTOTYPE_TAG);
    if (prototype.isEmpty())
      return;
    // todo - groups?
    CreateDefaultQueries.createDefaultQueries(prototype, query, factory);
  }

  @Nullable
  private ItemKeyGroup getItemKeyGroup(DistributionQueryNodeImpl query) {
    if (query.isPinned()) {
      EnumGrouping<?> grouping = myParams.getGroupingForTree();
      ResolvedItem resolved = grouping == null ? null : getResolvedItemForQueryGroup(query);
      return resolved == null ? null : grouping.getGroup(resolved);
    }
    return null;
  }

  private void placeQuery(DistributionQueryNode query, ItemKeyGroup group) {
    placeQueryUnder(group == null ? this : findOrCreateGroup(group), query, -1);
  }

  private DistributionGroupNodeImpl findOrCreateGroup(ItemKeyGroup group) {
    DistributionGroupNodeImpl groupNode = findGroup(group);
    if (groupNode == null) {
      groupNode = createGroupNode(group);
    }
    return groupNode;
  }

  private DistributionGroupNodeImpl createGroupNode(ItemKeyGroup group) {
    Configuration config = getConfiguration().createSubset(ConfigNames.KLUDGE_DISTRIBUTION_GROUP_TAG_NAME);
    DistributionGroupNodeImpl node = new DistributionGroupNodeImpl(config);
    //= new DistributionQueryNodeImpl("", config, Pair.create(descriptor, value));
//          query.getPresentation().setText(value.getDisplayName());
    node.updateGroup(group);
    placeGroupUnder(this, node);
    return node;
  }

  private DistributionGroupNodeImpl findGroup(ItemKeyGroup group) {
    TreeModelBridge<GenericNode> treeNode = getTreeNode();
    int count = treeNode.getChildCount();
    for (int i = 0; i < count; i++) {
      GenericNode node = treeNode.getChildAt(i).getUserObject();
      if (node instanceof DistributionGroupNodeImpl) {
        DistributionGroupNodeImpl groupNode = ((DistributionGroupNodeImpl) node);
        if (groupNode.isSameGroup(group)) {
          groupNode.updateGroup(group);
          return groupNode;
        }
      }
    }
    return null;
  }

  private static int placeQueryUnder(GenericNode node, DistributionQueryNode query, int startIdx) {
    ATreeNode<GenericNode> queryTreeNode = query.getTreeNode();
    ATreeNode<GenericNode> parentTreeNode = node.getTreeNode();
    int i = startIdx < 0 ? parentTreeNode.getChildCount() - 1 : startIdx;
    for (; i >= 0; i--) {
      ATreeNode<GenericNode> child = parentTreeNode.getChildAt(i);
      GenericNode sampleNode = child.getUserObject();
      if (!(sampleNode instanceof DistributionQueryNode))
        continue;
      DistributionQueryNode sampleQuery = ((DistributionQueryNode) sampleNode);
      int diff = DISTRIBUTION_QUERY_ORDER.compare(query, sampleQuery);
      if (diff >= 0)
        break;
    }
    parentTreeNode.insert(queryTreeNode, i + 1);
    return i + 1;
  }

  private void placeGroupUnder(GenericNode node, DistributionGroupNodeImpl group) {
    ATreeNode<GenericNode> groupTreeNode = group.getTreeNode();
    ATreeNode<GenericNode> parentTreeNode = node.getTreeNode();
    int count = parentTreeNode.getChildCount();
    int i;
    for (i = count - 1; i >= 0; i--) {
      ATreeNode<GenericNode> child = parentTreeNode.getChildAt(i);
      GenericNode sampleNode = child.getUserObject();
      if (!(sampleNode instanceof DistributionGroupNode))
        break;
      DistributionGroupNode sampleGroup = ((DistributionGroupNode) sampleNode);
      int diff = DISTRIBUTION_GROUP_ORDER.compare(group, sampleGroup);
      if (diff >= 0)
        break;
    }
    parentTreeNode.insert(groupTreeNode, i + 1);
  }

  @Nullable
  private ResolvedItem getResolvedItemForQueryGroup(DistributionQueryNodeImpl query) {
    ItemHypercube cube = getHypercube(false);
    Object obj = query.getResolvedItems(cube);
    if (obj instanceof ResolvedItem) {
      return (ResolvedItem) obj;
    } else if (obj instanceof List) {
      List list = (List) obj;
      if (list.size() > 0) {
        return Util.castNullable(ResolvedItem.class, list.get(0));
      }
    }
    return null;
  }

  private void removeValues(Collection<ItemKey> elements) {
    if (elements == null || elements.size() == 0)
      return;
    final Set<ItemKey> set;
    if (elements instanceof Set) {
      set = (Set<ItemKey>) elements;
    } else {
      set = Collections15.hashSet(elements);
    }
    Condition<ItemKey> condition = new Condition<ItemKey>() {
      public boolean isAccepted(ItemKey value) {
        return set.contains(value);
      }
    };
    removeValues(condition);
  }

  public void expandAfterNextUpdate() {
    myExpandAfterNextUpdate = true;
  }

  public int compareChildren(GenericNode node1, GenericNode node2) {
    int classDiff = ViewWeightManager.compare(node1, node2);
    if (classDiff != 0) {
      return classDiff;
    }
    if (node1 == null || node2 == null) {
      assert false;
      return 0;
    }
    if ((node1 instanceof DistributionQueryNode) && (node2 instanceof DistributionQueryNode)) {
      return DISTRIBUTION_QUERY_ORDER.compare((DistributionQueryNode) node1, (DistributionQueryNode) node2);
    } else if ((node1 instanceof DistributionGroupNode) && (node2 instanceof DistributionGroupNode)) {
      return DISTRIBUTION_GROUP_ORDER.compare((DistributionGroupNode) node1, (DistributionGroupNode) node2);
    } else {
      return NavigationTreeUtil.compareNodes(node1, node2);
    }
  }

  public ChildrenOrderPolicy getChildrenOrderPolicy() {
    return ChildrenOrderPolicy.ORDER_ALWAYS;
  }

  public void showExpadingNodeWhenInserted() {
    myShowExpandingNodeWhenInserted = true;
  }

  @Override
  public boolean canHideEmptyChildren() {
    return true;
  }

  public void setHideEmptyChildren(boolean newValue) {
    if(getHideEmptyChildren() != newValue) {
      myParams.setHideEmptyChildren(newValue);
      fireSubtreeChanged();
    }
  }

  public boolean getHideEmptyChildren() {
    return getParameters().isHideEmptyQueries();
  }

  public boolean isHidingEmptyChildren() {
    return myHidingChildren.hasHiddenOrNotCounted();
  }

  private static int comparePresentations(GenericNode o1, GenericNode o2) {
    return o1.getName().compareToIgnoreCase(o2.getName());
  }

  private static boolean invalidatePreview(GenericNodeImpl parent, Class<? extends GenericNodeImpl> stepInto) {
    Iterator<? extends GenericNode> it = parent.getChildrenIterator();
    IntArray indexes = new IntArray();
    int index = -1;
    while (it.hasNext()) {
      index++;
      GenericNode child = it.next();
      DistributionQueryNodeImpl query = Util.castNullable(DistributionQueryNodeImpl.class, child);
      if (query != null) {
        query.setPreviewSilent(null);
        query.invalidateChildren();
        indexes.add(index);
      } else if (stepInto != null) {
        GenericNodeImpl subNode = Util.castNullable(stepInto, child);
        if (subNode != null) invalidatePreview(subNode, null);
        else Log.error("Unexpected node " + child);
      }
    }
    boolean hasChanges = !indexes.isEmpty();
    if (hasChanges) {
      TreeModelBridge<GenericNode> treeNode = parent.getTreeNode();
      treeNode.fireChildrenChanged(indexes);
      parent.fireTreeNodeChanged();
    }
    return hasChanges;
  }

  @Override
  protected void invalidatePreview(RootNode root) {
    myChildrenUpdate.setDisabled(true);
    try {
      boolean changed = invalidatePreview(this, DistributionGroupNodeImpl.class);
      if (changed) fireTreeNodeChanged();
    } finally {
      myChildrenUpdate.setDisabled(false);
    }
    myChildrenUpdate.forceUpdate();
  }

  public void scheduleChildPreview(ItemsPreviewManager manager, boolean cancelOngoing) {
    myChildrenUpdate.maybeUpdate();
  }

  private static class NodeParams {
    private final DistributionFolderNodeImpl myNode;
    @NotNull
    private DistributionParameters myParameters;
    @Nullable
    private ConstraintDescriptor myDescriptor;
    @Nullable
    private EnumGrouping myGrouping;
    private AtomicReference<DetachComposite> myResolveLife = new AtomicReference<DetachComposite>(null);

    public NodeParams(DistributionFolderNodeImpl node) {
      myNode = node;
    }

    @NotNull
    public DistributionParameters getParameters() {
      return myParameters;
    }

    @Nullable
    public ConstraintDescriptor getDescriptor() {
      return myDescriptor;
    }

    public ConstraintType getConstraintType() {
      return  myDescriptor != null ? myDescriptor.getType() : null;
    }

    public void readConfig(Configuration configuration) {
      String attributeId = configuration.getSetting(ConfigNames.ATTRIBUTE_ID, null);
      myDescriptor = attributeId == null ? null :
        BaseEnumConstraintDescriptor.unresolvedDescriptor(attributeId, EnumConstraintKind.INCLUSION_OPERATION);
      endResolveLife();
      myParameters = DistributionParameters.readConfig(configuration);
    }

    private void endResolveLife() {
      DetachComposite life;
      while ((life = myResolveLife.get()) != null) {
        life.detach();
        myResolveLife.compareAndSet(life, null);
      }
    }

    public void setParameters(ConstraintDescriptor descriptor, DistributionParameters parameters) {
      myDescriptor = descriptor;
      endResolveLife();
      myParameters = parameters;
      myGrouping = null;
      resolveAttribute();
      writeConfig();
    }

    @Nullable
    private EnumGrouping<?> getGrouping() {
      String groupingName = myParameters.getGroupingName();
      if (groupingName == null)
        return null;
      if (myGrouping != null)
        return myGrouping;
      resolveGrouping();
      return myGrouping;
    }

    @Nullable
    private EnumGrouping<?> getGroupingForTree() {
      if (!myParameters.isArrangeInGroups())
        return null;
      else
        return getGrouping();
    }

    private boolean resolveAttribute() {
      // todo resolve grouping
      ConstraintDescriptor descriptor = myDescriptor;
      NameResolver resolver = myNode.getResolver();
      boolean resolved = false;
      if (descriptor != null) {
        if (resolver != null) {
          myDescriptor = descriptor.resolve(resolver, myNode.getHypercube(false), null);
          if (!(myDescriptor.getType() instanceof EnumConstraintType)) listenForDescriptor();
          else {
            endResolveLife();
            resolved = true;
          }
          resolveGrouping();
        }
      }
      return resolved;
    }

    private void listenForDescriptor() {
      while (myResolveLife.get() == null) {
        DetachComposite life = new DetachComposite();
        if (!myResolveLife.compareAndSet(null, life)) continue;
        myNode.myLife.lifespan().add(life);
        myNode.getResolver().getAllDescriptorsModel().addAWTChangeListener(life, new ChangeListener() {
          @Override
          public void onChange() {
            if (resolveAttribute()) {
              myNode.myLastCube = null;
              UPDATE_HYPERCUBE.addJob(myNode);
            }
          }
        });
      }
    }

    public void setHideEmptyChildren(boolean newValue) {
      myParameters = myParameters.setHideEmptyQueries(newValue);
      writeConfig();
    }

    private void writeConfig() {
      Configuration configuration = myNode.getConfiguration();
      if (myDescriptor != null) {
        configuration.setSetting(ConfigNames.ATTRIBUTE_ID, myDescriptor.getId());
      }
      myParameters.writeConfig(configuration);
    }

    private void resolveGrouping() {
      ConstraintDescriptor descriptor = getDescriptor();
      if (descriptor != null) {
        ConstraintType type = descriptor.getType();
        if (type instanceof EnumConstraintType) {
          myGrouping = null;
          String groupingName = getParameters().getGroupingName();
          if (groupingName != null) {
            List<EnumGrouping> groupings = ((EnumConstraintType) type).getAvailableGroupings();
            if (groupings != null) {
              for (EnumGrouping grouping : groupings) {
                if (Util.equals(groupingName, grouping.getDisplayableName())) {
                  myGrouping = grouping;
                  break;
                }
              }
            }
          }
        }
      }
    }

    @Override
    public String toString() {
      return "Params of " + myNode;
    }
  }

  private static class DistributionComparator implements Comparator<DistributionQueryNode> {
    @Nullable
    private final ItemHypercube myCube;

    public DistributionComparator(@Nullable ItemHypercube cube) {
      myCube = cube;
    }

    public DistributionComparator() {
      this(null);
    }

    public int compare(DistributionQueryNode o1, DistributionQueryNode o2) {
      if (o1 == o2)
        return 0;
      boolean pinned1 = o1.isPinned();
      boolean pinned2 = o2.isPinned();
      if (pinned1 && !pinned2)
        return -1;
      if (pinned2 && !pinned1)
        return 1;
      if (!pinned1 && !pinned2)
        return comparePresentations(o1, o2);

      // compare artifact keys
      Pair<ConstraintDescriptor, ItemKey> v1 = o1.getAttributeValue();
      Pair<ConstraintDescriptor, ItemKey> v2 = o2.getAttributeValue();
      if (v1 == null || v2 == null) {
        assert false : o1 + " " + o2;
        return 0;
      }
      if (!v1.getFirst().getId().equals(v2.getFirst().getId())) {
        assert false : v1 + " " + v2;
        return comparePresentations(o1, o2);
      }
      ConstraintType type = v1.getFirst().getType();
      if (!(type instanceof EnumConstraintType)) {
//        assert false : type;
        return comparePresentations(o1, o2);
      }
      EnumConstraintType enumType = ((EnumConstraintType) type);
      ItemHypercube cube = getCube(o1, o2);
      ItemOrder order1 = ItemKeys.getGroupOrder(enumType.resolveKey(v1.getSecond().getId(), cube));
      ItemOrder order2 = ItemKeys.getGroupOrder(enumType.resolveKey(v2.getSecond().getId(), cube));
      return order1.compareTo(order2);
    }

    private ItemHypercube getCube(DistributionQueryNode o1, DistributionQueryNode o2) {
      if (myCube != null) return myCube;
      GenericNode parent1 = o1.getParent();
      GenericNode parent2 = o2.getParent();
      GenericNode commonParent = parent1 == null ? parent2 : parent1;
      assert commonParent != null : o1 + " " + o2;
      assert parent1 == null || parent2 == null || parent1 == parent2 : parent1 + " " + parent2;
      return commonParent == null ? new ItemHypercubeImpl() : commonParent.getHypercube(false);
    }
  }


  private static class DistributionGroupComparator implements Comparator<DistributionGroupNode> {
    public int compare(DistributionGroupNode o1, DistributionGroupNode o2) {
      if (o1 == o2)
        return 0;
      GenericNode parent1 = o1.getParent();
      GenericNode parent2 = o2.getParent();
      GenericNode parent = parent1 == null ? parent2 : parent1;
      assert parent != null : o1 + " " + o2;
      assert parent1 == null || parent2 == null || parent1 == parent2 : parent1 + " " + parent2;
      if (!(parent instanceof DistributionFolderNodeImpl)) {
        // maybe both parents are null
        return 0;
      }
      DistributionFolderNodeImpl folder = ((DistributionFolderNodeImpl) parent);
      EnumGrouping grouping = folder.myParams.getGrouping();
      if (grouping == null) {
        return 0;
      }
      Comparator comparator = grouping.getComparator();
      if (comparator == null) {
        return comparePresentations(o1, o2);
      }

      ItemKeyGroup g1 = o1.getGroup();
      ItemKeyGroup g2 = o2.getGroup();

      if (g1 == null) {
        if (g2 == null) {
          return 0;
        } else {
          return 1;
        }
      } else {
        if (g2 == null) {
          return -1;
        } else {
          return comparator.compare(g1, g2);
        }
      }
    }
  }


  public static final class ExpandingProgressNode extends GenericNodeImpl {
    public ExpandingProgressNode(Configuration parentConfig) {
      super(new MyPresentation(), parentConfig.getOrCreateSubset(ConfigNames.EXPANDER_KEY));
    }

    @NotNull
    @ThreadSafe
    public QueryResult getQueryResult() {
      return QueryResult.NO_RESULT;
    }

    public boolean isCopiable() {
      return false;
    }

    private static final class MyPresentation implements CanvasRenderable {
      private Color myForeground;

      public void renderOn(Canvas canvas, CellState state) {
        if (!state.isSelected()) {
          if (myForeground == null)
            myForeground = ColorUtil.between(state.getDefaultForeground(), state.getDefaultBackground(), 0.5F);
          canvas.setForeground(myForeground);
        }
        canvas.appendText("Creating distribution\u2026");
      }
    }
  }
}