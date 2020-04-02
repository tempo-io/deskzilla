package com.almworks.actions;

import com.almworks.api.application.ExplorerComponent;
import com.almworks.api.application.tree.ConnectionNode;
import com.almworks.api.application.tree.GenericNode;
import com.almworks.api.engine.Connection;
import com.almworks.util.L;
import com.almworks.util.ui.UIComponentWrapper;
import com.almworks.util.ui.actions.*;

class ShowConnectionInfo extends AnAbstractAction {
  public ShowConnectionInfo() {
    super(L.actionName("Show Connection &Info"));
    setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, L.tooltip("Display connection configuration and stats"));
  }

  public void perform(ActionContext context) throws CantPerformException {
    GenericNode node = context.getSourceObject(GenericNode.NAVIGATION_NODE);
    ConnectionNode cnode = node.getAncestorOfType(ConnectionNode.class);
    assert cnode != null;
    Connection connection = cnode.getConnection();
    UIComponentWrapper component = connection.getConnectionStateComponent();
    if (component == null) {
      assert false : connection;
      component = UIComponentWrapper.Simple.message(L.content("No information available"));
    }
    context.getSourceObject(ExplorerComponent.ROLE).showComponent(component, cnode.getConnectionName());
  }

  public void update(UpdateContext context) throws CantPerformException {
    super.update(context);
    context.watchRole(GenericNode.NAVIGATION_NODE);
    ConnectionNode node;
    try {
      node = context.getSourceObject(GenericNode.NAVIGATION_NODE).getAncestorOfType(ConnectionNode.class);
    } catch (CantPerformException e) {
      node = null;
    }

    if (node == null) {
      context.setEnabled(EnableState.INVISIBLE);
      return;
    }

    Connection connection = node.getConnection();
    UIComponentWrapper component = connection.getConnectionStateComponent();
    context.setEnabled(component != null);
  }
}
