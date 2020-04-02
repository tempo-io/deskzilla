package com.almworks.api.application;

import com.almworks.api.application.tree.ConnectionNode;
import com.almworks.api.application.tree.GenericNode;
import com.almworks.api.engine.*;
import com.almworks.util.components.ATreeNode;
import com.almworks.util.model.CollectionModel;
import com.almworks.util.ui.TreeUtil;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.List;

/**
 * @author dyoma
 */
public abstract class DBDataRoles {
  public static final DataRole<Long> ITEM_ROLE = DataRole.createRole(Long.class);

  @NotNull
  public static Connection findConnection(ActionContext context) throws CantPerformException {
    try {
      return context.getSourceObject(ConnectionNode.CONNECTION_NODE).getConnection();
    } catch (CantPerformException e) {
      try {
        return context.getSourceObject(ConnectionNode.CONNECTION);
      } catch (CantPerformException e1) {
        List<ATreeNode<GenericNode>> nodes =
          GenericNode.GET_TREE_NODE.collectList(context.getSourceCollection(GenericNode.NAVIGATION_NODE));
        Connection connection = lookForConnectionInAncestors(TreeUtil.commonParent(nodes));
        if (connection != null)
          return connection;

        Engine engine = context.getSourceObject(Engine.ROLE);
        Collection<Connection> connections = engine.getConnectionManager().getReadyConnectionsModel().copyCurrent();
        if (connections.size() == 1) {
          return connections.iterator().next();
        }

        throw new CantPerformException(e);
      }
    }
  }

  @Nullable
  public static Connection lookForConnectionInAncestors(ATreeNode node) {
    while (node != null) {
      Object userObject = node.getUserObject();
      if (userObject instanceof ConnectionNode)
        return ((ConnectionNode) userObject).getConnection();
      node = node.getParent();
    }
    return null;
  }

  public static void watchConnection(UpdateRequest updateRequest) {
    updateRequest.watchRole(GenericNode.NAVIGATION_NODE);
  }

  /**
   * NB: this method enables context if it satisfies its conditions. Be sure that you do not disable context before calling this method, since disable will be overridden.
   */
  public static void setEnabledIfConnectionCapableOf(UpdateContext context, @Nullable Connection connection,
    Connection.Capability ... capabilities)
  {
    if (connection != null)
      for (Connection.Capability capability : capabilities) {
        if (connection.hasCapability(capability)) {
          context.setEnabled(true);
          return;
        }
      }
    context.setEnabled(false);
  }

  public static void checkAnyConnectionHasCapability(UpdateContext context, Connection.Capability capability) throws CantPerformException {
    ConnectionManager connectionManager = context.getSourceObject(Engine.ROLE).getConnectionManager();
    CollectionModel<Connection> connections = connectionManager.getConnections();
    context.getUpdateRequest().updateOnChange(connectionManager.getConnectionsModifiable());
    for (Connection someConnection : connections.copyCurrent()) {
      if (someConnection.hasCapability(capability)) {
        return;
      }
    }
    ActionUtil.setNoAction(context);
    throw new CantPerformException();
  }

  public static boolean isAnyConnectionHasAnyCapability(ActionContext context, Connection.Capability ... capabilities)
    throws CantPerformException
  {
    ConnectionManager manager = context.getSourceObject(Engine.ROLE).getConnectionManager();
    List<Connection> connections = manager.getConnections().copyCurrent();
    for (Connection connection : connections) {
      for (Connection.Capability capability : capabilities)
        if (connection.hasCapability(capability)) return true;
    }
    return false;
  }

  public static void checkExisting(ItemWrapper bug) throws CantPerformException {
    if (bug.services().isDeleted()) throw new CantPerformException("Item is deleted");
  }

  public static List<ItemWrapper> selectExisting(List<ItemWrapper> wrappers) {
    if (wrappers == null || wrappers.isEmpty()) return wrappers;
    List<ItemWrapper> copy = null;
    for (int i = 0; i < wrappers.size(); i++) {
      ItemWrapper wrapper = wrappers.get(i);
      if (wrapper.services().isDeleted()) {
        if (copy != null) continue;
        copy = Collections15.arrayList();
        if (i > 0) copy.addAll(wrappers.subList(0, i));
      } else if (copy != null) copy.add(wrapper);
    }
    return copy != null ? copy : wrappers;
  }
}
