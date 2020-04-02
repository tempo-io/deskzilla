package com.almworks.explorer.tree;

import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.qb.*;
import com.almworks.api.application.tree.*;
import com.almworks.api.config.ConfigNames;
import com.almworks.api.engine.Connection;
import com.almworks.api.syncreg.*;
import com.almworks.explorer.qbuilder.filter.BinaryCommutative;
import com.almworks.explorer.qbuilder.filter.FilterGramma;
import com.almworks.items.api.DBFilter;
import com.almworks.items.api.DBReader;
import com.almworks.tags.TagsComponent;
import com.almworks.util.components.ModelAware;
import com.almworks.util.config.Configuration;
import com.almworks.util.text.parser.FormulaWriter;
import com.almworks.util.text.parser.ParseException;
import com.almworks.util.threads.ThreadAWT;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.actions.CantPerformException;
import com.almworks.util.ui.actions.dnd.DragContext;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.List;

import static com.almworks.api.engine.Connection.Capability.EDIT_ITEM;

abstract class AbstractQueryNode extends GenericNodeImpl implements QueryNode, ModelAware, ConnectionAwareNode {
  private final QueryNodeResult myResult = new QueryNodeResult(this);
  private String myInitialFormula;
  private boolean myInitializationFinished = false;
  private volatile Boolean myCachedSynchronizedState = null;
  private int myLastPreviewCount = -1;

  public AbstractQueryNode(QueryPresentation presentation, Configuration config) {
    super(presentation, config);
    myInitialFormula = config.getSetting(ConfigNames.QUERY_FORMULA, null);
  }

  public boolean isNarrowing() {
    return true;
  }

  @ThreadAWT
  public boolean isSynchronized() {
    Threads.assertAWTThread();
    Boolean cached = myCachedSynchronizedState;
    if (cached != null) {
      return cached;
    }
    boolean result = getSynchronizedState();
    myCachedSynchronizedState = result;
    return result;
  }

  @ThreadAWT
  private boolean getSynchronizedState() {
    GenericNode parent = getParent();
    if (parent != null && parent.isSynchronized())
      return true;
    // todo should we check for generic locally-managed filters?
    if (isLocallyManagedFilter(getFilterStructure()))
      return true;
    RootNode root = getRoot();
    if (root == null)
      return false;
    SyncRegistry syncRegistry = root.getSyncRegistry();
    assert syncRegistry != null;
    boolean result;
    result = isSynchronizedByFlag(syncRegistry.getSyncFlagRegistry());
    if (result)
      return true;
    result = isSynchronizedByCube(syncRegistry.getSyncCubeRegistry());
    return result;
  }

  private boolean isLocallyManagedFilter(FilterNode node) {
    if (node == null)
      return false;
    if (node instanceof ConstraintFilterNode) {
      ConstraintDescriptor descriptor = ((ConstraintFilterNode) node).getDescriptor();
      if (descriptor != null && TagsComponent.TAGS.getId().equals(descriptor.getId())) {
        return true;
      }
    } else if (node instanceof BinaryCommutative) {
      BinaryCommutative composite = (BinaryCommutative) node;
      List<FilterNode> nodes = composite.getChildren();
      for (FilterNode n : nodes) {
        if (!isLocallyManagedFilter(n))
          return false;
      }
      return true;
    }
    return false;
  }

  @NotNull
  public QueryNodeResult getQueryResult() {
    return myResult;
  }

  public boolean isCopiable() {
    return areAllChildrenCopiable();
  }

  public void setFilter(FilterNode filter) {
    boolean isSame = filter != null && filter.isSame(getFilterStructure());
    myResult.setFilterTree(filter);
    writeFormula();
    if (!isSame) {
      setSyncFlag(false, false);
    }
  }

  public FilterNode getFilterStructure() {
    return myResult.getFilterTree();
  }

  public QueryPresentation getPresentation() {
    return (QueryPresentation) super.getPresentation();
  }

  @ThreadAWT
  public void checkSyncStateAndFire(boolean moreSynchronized, boolean lessSynchronized) {
    Boolean cached = myCachedSynchronizedState;
    if (cached != null) {
      if ((cached && lessSynchronized) || (!cached && moreSynchronized)) {
        myCachedSynchronizedState = null;
      }
    }
    boolean changed = updateSyncState();
    if (changed) {
      fireTreeNodeChanged();
    }
  }

  public int compareTo(GenericNode genericNode) {
    int i = super.compareTo(genericNode);
    if (i != 0)
      return i;
    return String.valueOf(this).compareToIgnoreCase(String.valueOf(genericNode));
  }


  /**
   * @param precise if true, returns a hypercube that exactly corresponds to this query (or null);
   *                if false, returns a smallest encompassing hypercube
   */
  @Nullable(
    documentation = "when there's no valid contstraint or when precise hypercube is requested and is not available")
  public ItemHypercube getHypercube(boolean precise) {
    return myResult.getHypercube(precise);
  }

  public void onInsertToModel() {
    super.onInsertToModel();
    watchResult();
    myInitializationFinished = false;
    maybeFinishInitialization(null);
    myCachedSynchronizedState = null;
  }

  public void onConnectionState(ConnectionNode connection, boolean ready) {
    maybeFinishInitialization(connection);
    myCachedSynchronizedState = null;
  }

  public void onRemoveFromModel() {
    myResult.detach();
    super.onRemoveFromModel();
  }

  @ThreadAWT
  private void maybeFinishInitialization(ConnectionNode connectionNode) {
    Threads.assertAWTThread();
    if (myInitializationFinished)
      return;
    if (connectionNode == null)
      connectionNode = getAncestorOfType(ConnectionNode.class);
    else
      assert connectionNode == getAncestorOfType(ConnectionNode.class);
    if (isNode() && (connectionNode == null || connectionNode.isConnectionReady())) {
      // NB: order of initialization is significant!
      // was: watchCounter();
      myResult.updateFilter(false);
      myInitializationFinished = true;
      fireIfChanged();
    }
  }


  private void watchResult() {
    GenericNode parent = getParent();
    assert parent != null : this;
    myResult.attach();
  }

  protected void onQueryNodeResultStirred(boolean updated) {
    if (!isNode())
      return;
    if (updated) {
      invalidatePreview();
      myCachedSynchronizedState = null;
    }
    fireIfChanged();
  }

  private void fireIfChanged() {
    boolean changed = updateIcon();
    changed |= updateSyncState();
    if (changed)
      fireTreeNodeChanged();
  }

  /**
   * Sets the "synchronized" flag for the node.
   * <p/>
   * When sync is true, the node is marked synchronized. If there's a precise hypercube for this node,
   * it is also marked as synchronized.
   * <p/>
   * When sync is false, the node is marked unsynchonized. Cubes are affected only if unsyncCubes is true. In
   * that case, an encompassing cube is marked as unsynced.
   */
  @ThreadAWT
  public void setSyncFlag(boolean sync, boolean unsyncCubes) {
    Threads.assertAWTThread();
    RootNode root = getRoot();
    if (root == null)
      return;
    Connection connection = getConnection();
    if (connection == null)
      return;
    SyncRegistry syncRegistry = root.getSyncRegistry();
    if (syncRegistry == null)
      return;
    syncRegistry.lockUpdate();
    try {
      SyncFlagRegistry flagRegistry = syncRegistry.getSyncFlagRegistry();
      if (flagRegistry != null) {
        flagRegistry.setSyncFlag(connection.getConnectionID(), getNodeId(), sync);
      }
      SyncCubeRegistry cubeRegistry = syncRegistry.getSyncCubeRegistry();
      if (cubeRegistry != null) {
        if (sync) {
          ItemHypercube cube = getHypercube(true);
          if (cube != null) {
            cubeRegistry.setSynced(cube);
          }
        } else if (unsyncCubes) {
          // delay this feature
          // it may destroy large sync status because of a single action

//          Hypercube<ArtifactPointer, ArtifactPointer> cube = getHypercube(false);
//          if (cube != null) {
//            cubeRegistry.setUnsynced(cube);
//          }
        }
      }
    } finally {
      try {
        syncRegistry.unlockUpdate();
      } catch (Exception e) {
        // ignore
      }
    }

    // if we were unsynced and become synced, and we have 0 count, the tree may hide this node
    // but then preview may be generated that shows >0 items so the node will appear again
    // avoid this blinking by invalidating cached preview count
    myLastPreviewCount = -1;

    // manually clear preview before firing events with checkSync...
    setPreview(null);

    // synchronized
    checkSyncStateAndFire(sync, !sync);

    // now full invalidate and schedule recount
    invalidatePreview();
  }

/*
  public String toString() {
    return getName();
  }
*/

  protected void initializeFormula() {
    FilterNode existing = getFilterStructure();
    if (existing != null && existing != FilterNode.ALL_ITEMS)
      return;
    if (myInitialFormula != null) {
      try {
        myResult.setFilterTree(FilterGramma.parse(myInitialFormula));
        return;
      } catch (ParseException e) {
        Log.warn("cannot parse [" + myInitialFormula + "]", e);
      } finally {
        // release mem
        myInitialFormula = null;
      }
    }
    setFilter(FilterGramma.createEmpty());
  }

  protected void onFirstInsertToModel() {
    getPresentation().setNode(this);
    initializeFormula();
  }

  protected boolean updateIcon() {
    return getPresentation().setCanRunNow(myResult.canRunNow());
  }

  boolean updateSyncState() {
    boolean sync = isSynchronized();
    boolean changed = getPresentation().setSynced(sync);
    if (changed) {
      if (!sync) {
        myResult.forceVersionIncrease();
      }
      invalidatePreview();
    }
    return changed;
  }

  private boolean isSynchronizedByCube(SyncCubeRegistry cubeRegistry) {
    if (cubeRegistry == null)
      return false;
    ItemHypercube cube = getHypercube(false);
    // we always have encompassing hypercube, at least "U" -??
    //assert cube != null : this;
    if (cube == null)
      return false;
    return cubeRegistry.isSynced(cube);
  }

  private boolean isSynchronizedByFlag(SyncFlagRegistry flagRegistry) {
    if (flagRegistry == null)
      return false;
    Connection connection = getConnection();
    return connection != null && flagRegistry.isSyncFlag(connection.getConnectionID(), getNodeId());
  }

  private void writeFormula() {
    getConfiguration().setSetting(ConfigNames.QUERY_FORMULA, FormulaWriter.write(getFilterStructure()));
  }

  boolean isFullyInitialized() {
    return myInitializationFinished;
  }

  /**
   * This method calculates the preview. It does not
   * change this instance fields.
   */
  @Nullable
  protected ItemsPreview calculatePreview(Lifespan lifespan, DBReader reader) {
    return calculateFilteringNodePreview(this, lifespan, reader);
  }

  protected static ItemsPreview calculateFilteringNodePreview(GenericNode node, Lifespan lifespan, DBReader reader) {
    QueryResult queryResult = node.getQueryResult();
    if (queryResult == null)
      return new ItemsPreview.Unavailable();
    DBFilter view = queryResult.getDbFilter();
    if (view == null)
      return new ItemsPreview.Unavailable();
    if (lifespan.isEnded())
      return null;
    return CountPreview.scanView(view, lifespan, reader);
  }

  @ThreadAWT
  protected void updatePreviewBasedValues(ItemsPreview preview) {
    super.updatePreviewBasedValues(preview);
    if (preview != null && preview.isValid() && preview.isAvailable()) {
      myLastPreviewCount = preview.getItemsCount();
    }
  }

  public int getCusionedPreviewCount() {
    return myLastPreviewCount;
  }

  public boolean canAcceptItems(List<ItemWrapper> wrappers, DragContext context) throws CantPerformException {
    Connection connection = getConnection();
    if (connection == null || !connection.hasCapability(EDIT_ITEM)) {
      return false;
    }
    return QueryDropAcceptor.canAcceptItems(wrappers, this, context);
  }

  public void acceptItems(final List<ItemWrapper> wrappers, DragContext context) {
    Connection connection = getConnection();
    if (connection != null && connection.hasCapability(EDIT_ITEM)) {
      QueryDropAcceptor.acceptItems(wrappers, this, context);
    }
  }
}
