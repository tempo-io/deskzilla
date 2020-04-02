package com.almworks.actions.order;

import com.almworks.api.application.LoadedItem;
import com.almworks.api.application.order.Order;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.Connection;
import com.almworks.api.gui.*;
import com.almworks.edit.EditLifecycleImpl;
import com.almworks.explorer.ColumnsCollector;
import com.almworks.gui.ArtifactTableColumns;
import com.almworks.util.Terms;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.config.Configuration;
import com.almworks.util.i18n.Local;
import org.almworks.util.Collections15;

import java.util.Collection;
import java.util.Set;

/**
 * @author dyoma
 */
class ItemsLoader {
  private ItemsLoader() {}

  public static void showReorderWindow(ComponentContainer container, Collection<? extends LoadedItem> items,
    Connection sourceConnection)
  {
    Collection<Connection> connections = getConnections(items);
    if (connections.size() == 0) {
      showErrorMessage(container, Local.parse(Terms.ref_Artifacts) + " don't belong to any connection");
    } else if (connections.size() > 1) {
      showErrorMessage(container, "Cannot reorder " + Local.parse(Terms.ref_artifacts) + " from different connections");
    } else {
      Connection connection = connections.iterator().next();
      AListModel<? extends Order> orders = connection.getOrdersModel();
      if (orders.getSize() == 0) {
        showErrorMessage(container, "There are no fields usable for reordering");
      } else {
        WindowManager windowManager = container.getActor(WindowManager.ROLE);
        assert windowManager != null;
        FrameBuilder builder = windowManager.createFrame("reorderAction");
        Configuration config = builder.getConfiguration()
          .getOrCreateSubset("reorderWindow")
          .getOrCreateSubset("Connection:" + connection.getConnectionID());
        OrderSelector selector = new OrderSelector(config, orders);
        if (!selector.ensureOrderSelected(getDialogManager(container)))
          return;
        ColumnsCollector collector = container.getActor(ColumnsCollector.ROLE);
        assert collector != null;
        ArtifactTableColumns<LoadedItem> columns = collector.getColumns(sourceConnection);
        EditLifecycleImpl lifecycle = EditLifecycleImpl.create(builder, null);
        final ReorderWindow reorderWindow =
          new ReorderWindow(items, selector, columns, config, builder.getWindowContainer(), lifecycle);
        builder.setContent(reorderWindow);
        builder.setTitle("Reorder by Field");
        lifecycle.setDiscardConfirmation(reorderWindow.createCloseConfirmation());
        builder.showWindow();
      }
    }
  }

  private static void showErrorMessage(ComponentContainer container, String message) {
    getDialogManager(container).showErrorMessage("Cannot Reorder", message);
  }

  private static DialogManager getDialogManager(ComponentContainer container) {
    DialogManager dialogManager = container.getActor(DialogManager.ROLE);
    assert dialogManager != null;
    return dialogManager;
  }

  private static Collection<Connection> getConnections(Collection<? extends LoadedItem> items) {
    Set<Connection> result = Collections15.hashSet();
    for (LoadedItem item : items) {
      Connection connection = item.getConnection();
      if (connection != null)
        result.add(connection);
    }
    return result;
  }
}
