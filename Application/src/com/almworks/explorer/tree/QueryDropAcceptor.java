package com.almworks.explorer.tree;

import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.tree.QueryNode;
import com.almworks.api.engine.Connection;
import com.almworks.api.engine.Engine;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.api.syncreg.ItemHypercubeUtils;
import com.almworks.util.exec.Context;
import com.almworks.util.ui.GlobalColors;
import com.almworks.util.ui.actions.*;
import com.almworks.util.ui.actions.dnd.*;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public class QueryDropAcceptor {
  private static final TypedKey<Map<QueryNode, Boolean>> ACCEPT_MAP = TypedKey.create("ACCEPT_MAP");
  private static final TypedKey<Map<QueryNode, AnAction>> COPY_DROP_ACTION_MAP = TypedKey.create("COPY_DROP_ACTION_MAP");
  private static final TypedKey<Map<QueryNode, AnAction>> MOVE_DROP_ACTION_MAP = TypedKey.create("MOVE_DROP_ACTION_MAP");

  public static boolean canAcceptItems(List<ItemWrapper> wrappers, AbstractQueryNode node,
    DragContext context) throws CantPerformException
  {
    AnAction dropAction = getDropAction(wrappers, node, context);
    UpdateContext uc = getUpdateContext(context, wrappers);
    dropAction.update(uc);
    boolean enabled = uc.getEnabled() == EnableState.ENABLED;
    if (enabled) {
      String description = uc.getPresentationProperty(PresentationKey.SHORT_DESCRIPTION);
      if (description != null && description.length() > 0) {
        StringDragImageFactory.ensureContext(context, DndUtil.ACTION_IMAGE_FACTORY, description, uc.getComponent(),
          GlobalColors.DRAG_AND_DROP_DARK_COLOR);
      }
    }
    return enabled;
  }

  public static void acceptItems(List<ItemWrapper> wrappers, AbstractQueryNode node, DragContext context) {
    AnAction dropAction = getDropAction(wrappers, node, context);
    try {
      UpdateContext uc = getUpdateContext(context, wrappers);
      dropAction.update(uc);
      if (uc.getEnabled() == EnableState.ENABLED) {
        dropAction.perform(uc);
      }
    } catch (CantPerformException e) {
      Log.debug("can't drop", e);
    }
  }

  @NotNull
  private static AnAction getDropAction(List<ItemWrapper> wrappers, AbstractQueryNode node,
    DragContext context)
  {
    boolean move = context.getAction() == DndAction.MOVE;
    TypedKey<Map<QueryNode, AnAction>> mapkey = move ? MOVE_DROP_ACTION_MAP : COPY_DROP_ACTION_MAP;
    AnAction dropAction = null;
    Map<QueryNode, AnAction> map = context.getValue(mapkey);
    if (map == null) {
      map = Collections15.hashMap();
      context.putValue(mapkey, map);
    } else {
      dropAction = map.get(node);
    }

    if (dropAction == null) {
      ItemHypercube hypercube = node.getHypercube(true);
      dropAction = getDropAction(wrappers, hypercube, context, "drop." + node.getNodeId(), move);
      if (dropAction == null)
        dropAction = AnAction.INVISIBLE;
      map.put(node, dropAction);
    }
    return dropAction;
  }

  private static UpdateContext getUpdateContext(DragContext context, List<ItemWrapper> wrappers) {
    Component c = context.getComponent();
    if (c == null) {
      assert false;
      c = new JLabel();
    }
    DropUpdateContext r = new DropUpdateContext(c, context);
    r.setWrappers(wrappers);
    return r;
  }

  private static AnAction getDropAction(List<ItemWrapper> wrappers, @Nullable ItemHypercube hypercube,
    DragContext context, String frameId, boolean move)
  {
    if (hypercube == null)
      return null;
    if (wrappers == null || wrappers.isEmpty())
      return null;
    Collection<Long> connectionItems = ItemHypercubeUtils.getIncludedConnections(hypercube);
    int connectionCount = connectionItems.size();
    if (connectionCount != 1)
      return null;
    // check items are in the same connection
    Long connectionItem = connectionItems.iterator().next();
    for (ItemWrapper wrapper : wrappers) {
      Connection c = wrapper.getConnection();
      if (c == null || !Util.equals(c.getConnectionItem(), connectionItem)) {
        return null;
      }
    }
    Connection connection = Context.require(Engine.class).getConnectionManager().findByItem(connectionItem);
    if (connection == null)
      return null;

    return connection.createDropChangeAction(hypercube, frameId, move);
  }


  private static class DropUpdateContext extends DefaultUpdateContext {
    private final DragContext myParent;
    private List<ItemWrapper> myWrappers;

    public DropUpdateContext(Component c, DragContext context) {
      super(c, Updatable.NEVER);
      myParent = context;
    }

    @NotNull
    public <T> List<T> getSourceCollection(@NotNull TypedKey<? extends T> role) throws CantPerformException {
      if (role == ItemWrapper.ITEM_WRAPPER) {
        assert myWrappers != null;
        return (List<T>) myWrappers;
      }
      List<T> r = null;
      try {
        r = myParent.getSourceCollection(role);
      } catch (CantPerformException e) {
        // fall through
      }
      if (r != null && !r.isEmpty()) {
        return r;
      }
      return super.getSourceCollection(role);
    }

    public void setWrappers(List<ItemWrapper> wrappers) {
      myWrappers = wrappers;
    }
  }
}
