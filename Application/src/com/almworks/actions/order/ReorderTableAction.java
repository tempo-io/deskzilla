package com.almworks.actions.order;

import com.almworks.api.application.ItemCollectionContext;
import com.almworks.api.application.LoadedItem;
import com.almworks.api.application.order.Order;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.Connection;
import com.almworks.api.explorer.TableController;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Collections15;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author dyoma
 */
public class ReorderTableAction extends SimpleAction {
  public static final AnAction INSTANCE = new ReorderTableAction();
  
  public ReorderTableAction() {
    super("Reorder by &Field\u2026", Icons.ACTION_REORDER);
    watchRole(TableController.DATA_ROLE);
    setDefaultPresentation(PresentationKey.SHORTCUT, KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK));
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    TableController controller = context.getSourceObject(TableController.DATA_ROLE);
    AListModel<? extends LoadedItem> model = controller.getCollectionModel();
    context.updateOnChange(model);
    boolean enabled = controller.getItemCollectionContext() != null && model.getSize() >= 2;
    if (enabled) {
      LoadedItem a = model.getAt(0);
      Connection connection = a.getConnection();
      if (connection == null) {
        enabled = false;
      } else {
        if (!connection.hasCapability(Connection.Capability.EDIT_ITEM)) enabled = false;
        else {
          AListModel<? extends Order> orders = connection.getOrdersModel();
          enabled = orders.getSize() > 0;
        }
      }
    }
    context.setEnabled(enabled);
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    TableController controller = context.getSourceObject(TableController.DATA_ROLE);
    AListModel<? extends LoadedItem> artifactsModel = controller.getCollectionModel();
    List<LoadedItem> items = Collections15.arrayList(Collections15.linkedHashSet(artifactsModel.toList()));
    ItemCollectionContext contextInfo = controller.getItemCollectionContext();
    assert contextInfo != null;
    ItemsLoader.showReorderWindow(context.getSourceObject(ComponentContainer.ROLE), items,
      contextInfo.getSourceConnection());
  }
}
