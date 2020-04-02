package com.almworks.actions.create;

import com.almworks.api.edit.CreateItemPolicy;
import com.almworks.api.engine.Connection;
import com.almworks.api.explorer.ItemModelRegistry;
import com.almworks.api.gui.FrameBuilder;
import com.almworks.api.gui.WindowController;
import com.almworks.edit.EditLifecycleImpl;
import com.almworks.items.api.DBIdentifiedObject;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.i18n.Local;
import com.almworks.util.ui.UIComponentWrapper;
import com.almworks.util.ui.actions.*;
import org.almworks.util.detach.Detach;

import javax.swing.*;
import java.awt.*;

/**
 * @author : Dyoma
 */
class NewItemAction extends SimpleAction {
  private final CreateItemPolicy<?> myPolicy;

  public NewItemAction(String name, String description, Icon icon, CreateItemPolicy<?> policy) {
    super((String)null, icon);
    myPolicy = policy;
    setDefaultText(PresentationKey.NAME, name);
    setDefaultText(PresentationKey.SHORT_DESCRIPTION, description);
  }

  protected void doPerform(ActionContext actionContext) throws CantPerformException {
    Object contextData = myPolicy.prepareContextData(actionContext);
    NewItemContext context = new NewItemContext(actionContext);
    ItemModelRegistry registry = actionContext.getSourceObject(ItemModelRegistry.ROLE);
    final CreateItemHelper helper = context.getCreateArtifactHelper();
    final Connection connection = context.getConnection();
    final DBIdentifiedObject type = context.getItemType();

    if (activateExistingWindow(helper, connection, type))
      return;

    NewArtifactForm content = new NewArtifactForm();
    FrameBuilder builder = createWindow(context, content);

    EditLifecycleImpl editLife = EditLifecycleImpl.create(builder, null);

    WindowController controller = builder.showWindow(new Detach() {
      protected void doDetach() {
        helper.unregisterWindow(connection, type);
      }
    });
    NewItemManager.start(builder.getConfiguration().getOrCreateSubset("manager"), content, type, (CreateItemPolicy<Object>) myPolicy,
      contextData, editLife, registry, controller);
    helper.registerWindow(connection, type, controller);
  }

  private static boolean activateExistingWindow(CreateItemHelper helper, Connection connection, DBIdentifiedObject type) {
    WindowController existingWindow = helper.getNewItemWindow(connection, type);
    if (existingWindow != null) {
      existingWindow.activate();
      return true;
    }
    return false;
  }

  private FrameBuilder createWindow(NewItemContext context, UIComponentWrapper content) throws CantPerformException {
    final Connection connection = context.getConnection();
    final DBIdentifiedObject type = context.getItemType();
    String windowId = "new." + connection.getConnectionID() + "." + type;

    FrameBuilder builder = context.getWindowManager().createFrame(windowId);
    builder.setTitle(L.frame(Local.parse("New\u2026 - " + Terms.ref_Deskzilla)));
    builder.setPreferredSize(new Dimension(700, 750));
    builder.setActionScope("NewArtifact");

    builder.setContent(content);
    return builder;
  }

  protected void customUpdate(UpdateContext updateContext) throws CantPerformException {
    NewItemContext.watch(updateContext.getUpdateRequest());
    NewItemContext context = new NewItemContext(updateContext);
    CantPerformException.ensure(context.getConnection().hasCapability(Connection.Capability.CREATE_ITEM));
    context.getCreateArtifactHelper();
    myPolicy.update(updateContext);
  }
}
