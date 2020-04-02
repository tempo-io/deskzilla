package com.almworks.bugzilla.gui.flags.edit;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ModelMap;
import com.almworks.api.explorer.util.UIControllerUtil;
import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.bugzilla.provider.datalink.flags2.*;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.items.sync.SyncState;
import com.almworks.util.advmodel.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.completion.CompletingComboBox;
import com.almworks.util.components.completion.CompletingComboBoxController;
import com.almworks.util.components.recent.RecentController;
import com.almworks.util.ui.widgets.*;
import com.almworks.util.ui.widgets.util.ComponentWidget;
import com.almworks.util.ui.widgets.util.WidgetUtil;
import org.almworks.util.TypedKey;
import org.almworks.util.Util;
import org.almworks.util.detach.DetachComposite;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;

class RequesteeWidget extends ComponentWidget<EditableFlag> implements WidgetAttach {
  public static final RequesteeWidget INSTANCE = new RequesteeWidget();
  private static final TypedKey<Dimension> FIELD_SIZE = TypedKey.create("fieldSize");
  private static final TypedKey<AListModel<ItemKey>> USERS = TypedKey.create("users");
  private static final TypedKey<DetachComposite> LIFESPAN = TypedKey.create("requesteeLife");
  private static final CanvasRenderer<ItemKey> REQUESTEE_RENDERER = User.createRenderer("<None>");

  RequesteeWidget() {
    super(FILL_H_CENTER_V);
  }

  @Override
  public void activate(@NotNull HostCell cell) {
    EditableFlag flag = cell.restoreValue(this);
    doActivate(cell);
    updateComponentVisibility(cell);
  }

  @Override
  public void deactivate(@NotNull HostCell cell, JComponent liveComponent) {
    super.deactivate(cell, liveComponent);
  }

  @Override
  public WidgetAttach getAttach() {
    return this;
  }

  @Override
  public void attach(@NotNull WidgetHost host) {
    DetachComposite life = host.getWidgetData(LIFESPAN);
    if (life == null) {
      life = new DetachComposite();
      host.putWidgetData(LIFESPAN, life);
    }
    ModelMap modelMap = host.getWidgetData(FlagEditor.KEY_MODEL);
    ItemHypercubeImpl cube = FlagEditor.createConnectionCube(modelMap);
    BugzillaConnection bzConn = BugzillaConnection.getInstance(modelMap);
    if(bzConn != null) {
      CommonMetadata md = bzConn.getContext().getMetadata();
      BaseEnumConstraintDescriptor users = md.getEnumDescriptor(BugzillaAttribute.ASSIGNED_TO);
      AListModel<ItemKey> usersModel = UIControllerUtil.getArtifactListModel(life, cube, users, false);
      usersModel = SegmentedListModel.prepend(ItemKey.INVALID, usersModel);
      host.putWidgetData(USERS, usersModel);
    }
  }

  @Override
  public void detach(@NotNull WidgetHost host) {
    DetachComposite life = host.getWidgetData(LIFESPAN);
    if (life != null) {
      life.detach();
      host.putWidgetData(LIFESPAN, null);
    }
    host.putWidgetData(USERS, null);
  }

  /**
   * @return true iff previously invisible component just becomes visible
   */
  private boolean updateComponentVisibility(CellContext context) {
    HostCell cell = context.getActiveCell();
    if (cell == null) return false;
    EditableFlag flag = cell.restoreValue(this);
    if (flag == null) removeComponent(context);
    else if (isComboVisible(flag)) return showComponent(cell);
    else removeComponent(context);
    return false;
  }

  private boolean isComboVisible(EditableFlag flag) {
    if (flag.isDeleted() || flag.getStatus() != FlagStatus.QUESTION) return false;
    FlagTypeItem type = flag.getType();
    return type != null && type.allowsStatus(FlagStatus.QUESTION) && !type.isSureSpecificallyRequestable(false);
  }

  @Override
  protected JComponent obtainComponent(final HostCell cell) {
    final CompletingComboBox<ItemKey> comboBox = createComponent(cell);
    EditableFlag flag = cell.restoreValue(this);
    CompletingComboBoxController<ItemKey> controller = comboBox.getController();
    controller.setVariantsModel(FlagEditor.getRecentConfig(cell, "requestee"), cell.getHost().getWidgetData(USERS));
    setSelectedRequestee(comboBox, flag);
    controller.getModel().addSelectionChangeListener(cell.getActiveLife(), new ChangeListener() {
      @Override
      public void onChange() {
        if (!cell.isActive()) return;
        AComboboxModel<ItemKey> model = comboBox.getAModel();
        ItemKey requestee = RecentController.unwrap(model.getSelectedItem());
        if (requestee == ItemKey.INVALID) requestee = null;
        EditableFlag flag = cell.restoreValue(RequesteeWidget.this);
        ModelMap modelMap = cell.getHost().getWidgetData(FlagEditor.KEY_MODEL);
        FlagsModelKey.setRequestee(modelMap, flag, requestee);
      }
    });
    return comboBox;
  }

  private void setSelectedRequestee(CompletingComboBox<ItemKey> comboBox, EditableFlag flag) {
    ItemKey requestee = flag.getRawRequestee();
    if (requestee == null) requestee = ItemKey.INVALID;
    CompletingComboBoxController<ItemKey> controller = comboBox.getController();
    if (!Util.equals(RecentController.unwrap(controller.getSelectedItem()), requestee)) controller.setSelectedItem(requestee);
  }

  private CompletingComboBox<ItemKey> createComponent(CellContext context) {
    CompletingComboBox<ItemKey> cb = new CompletingComboBox<ItemKey>();
    cb.setCasesensitive(false);
    CompletingComboBoxController<ItemKey> controller = cb.getController();
    controller.setConvertors(ItemKey.DISPLAY_NAME, User.userResolver, ItemKey.DISPLAY_NAME_EQUALITY);
    controller.setCanvasRenderer(REQUESTEE_RENDERER);
    controller.setIdentityConvertor(ItemKey.GET_ID);
    return cb;
  }

  @Override
  protected Dimension getPreferedSize(CellContext context, EditableFlag value) {
    Dimension size = context.getHost().getWidgetData(FIELD_SIZE);
    if (size == null) {
      CompletingComboBox<ItemKey> field = createComponent(context);
      size = field.getPreferredSize();
      context.getHost().putWidgetData(FIELD_SIZE, size);
    }
    return new Dimension(size);
  }

  @Override
  public void processEvent(@NotNull EventContext context, @Nullable EditableFlag flag, TypedKey<?> reason) {
    super.processEvent(context, flag, reason);
    if (reason == EventContext.VALUE_CHANGED) {
      boolean shown = updateComponentVisibility(context);
      CompletingComboBox<ItemKey> component = (CompletingComboBox<ItemKey>) context.getLiveComponent();
      if (component != null && flag != null) {
        if (shown && flag.isEdited()) component.requestFocusInWindow();
        setSelectedRequestee(component, flag);
      }
    }
  }

  @Override
  public void paint(@NotNull GraphContext context, @Nullable EditableFlag value) {
    if (context.getLiveComponent() == null) {
      ItemKey requestee = value == null ? null : value.getRequestee();
      if (requestee != null) {
        if (value.getSyncState() == SyncState.LOCAL_DELETE) // DZO-757
          context.setColor(FlagEditor.removedColor(context));
        context.drawTrancatableString(0, WidgetUtil.centerYText(context), requestee.getDisplayName());
      }
    }
    super.paint(context, value);
  }
}
