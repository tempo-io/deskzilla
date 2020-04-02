package com.almworks.engine.gui.attachments;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.UIController;
import com.almworks.api.download.DownloadedFile;
import com.almworks.api.download.FileDownloadListener;
import com.almworks.api.engine.Connection;
import com.almworks.engine.gui.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.components.ASortedTable;
import com.almworks.util.config.Configuration;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Base UI controller responsible for attachments in New/Edit Item forms.
 */
public abstract class BaseAttachmentsController <A extends Attachment, C extends Collection<A>>
  implements UIController, AttachmentsController, FileDownloadListener, ChangeListener
{
  private final AttachmentDownloadStatus<A> myStatus = new AttachmentDownloadStatus<A>(this);
  private final AttachmentsTable<A> myTable;
  private final List<JComponent> myHideComponents = Collections15.arrayList();
  private final ModelKey<C> myModelKey;
  private final Configuration myViewConfig;
  private final DBAttribute<Long> myAttrMaster;

  protected BaseAttachmentsController(ASortedTable<A> table, ModelKey<C> modelKey, Configuration viewConfig, DBAttribute<Long> attrMaster) {
    myModelKey = modelKey;
    myAttrMaster = attrMaster;
    myTable = new AttachmentsTable<A>(myStatus, table);
    myViewConfig = viewConfig;

    final JComponent component = myTable.getComponent();
    myHideComponents.add(component);
    component.setVisible(false);
  }

  public void initialize(boolean creator) {
    myTable.initialize(getAttachmentProperties(creator), myViewConfig, getDeleteAction());
    myTable.setTooltipProvider(getTooltipProvider());
  }

  protected abstract List<AttachmentProperty<? super A, ?>> getAttachmentProperties(boolean creator);

  protected abstract AnAction getDeleteAction();

  protected abstract AttachmentTooltipProvider<A> getTooltipProvider();

  public void addHideComponent(JComponent component) {
    myHideComponents.add(component);
    component.setVisible(isVisible());
  }

  @Override
  public void connectUI(@NotNull Lifespan lifespan, @NotNull final ModelMap model, @NotNull JComponent component) {
    final OrderListModel<A> attachmentsModel = OrderListModel.create();
    final ChangeListener updateList = new ChangeListener() {
      public void onChange() {
        C attachments = myModelKey.getValue(model);
        if(attachments == null) {
          attachments = emptyCollection();
        }
        attachmentsModel.replaceElementsSet(attachments);
      }
    };
    model.addAWTChangeListener(lifespan, updateList);
    final Connection connection = LoadedItemServices.VALUE_KEY.getValue(model).getConnection();
    myStatus.watch(lifespan, attachmentsModel, connection);
    myTable.setCollectionModel(lifespan, attachmentsModel);
    attachmentsModel.addAWTChangeListener(lifespan, this);
    updateList.onChange();
    ExternalAddedSlavesListener.attach(lifespan, model, component, myModelKey, myAttrMaster);
  }

  protected abstract C emptyCollection();

  @Override
  public Collection<? extends Attachment> getAttachments() {
    return myTable.getAttachments();
  }

  @Override
  public void showAttachment(Attachment attachment, @Nullable Component parentComponent) {
    AttachmentsControllerUtil.downloadAndShowAttachment(
      this, attachment, parentComponent(parentComponent), myViewConfig);
  }

  private Component parentComponent(Component parentComponent) {
    if(parentComponent == null) {
      parentComponent = myTable.getComponent();
    }
    return parentComponent;
  }

  public DownloadedFile getDownloadedFile(Attachment attachment) {
    return myStatus.getDownloadedFile(attachment);
  }

  public Modifiable getAllAttachmentsModifiable() {
    return myTable.getAComponent().getCollectionModel();
  }

  public void initiateDownload(Attachment attachment) {
    AttachmentsControllerUtil.initiateDownload(myStatus.getContext(), attachment);
  }

  public void saveAs(File file, Component component) {
    AttachmentUtils.saveAs(file, parentComponent(component), myViewConfig);
  }

  public Configuration getViewConfig() {
    return myViewConfig;
  }

  public Modifiable getDownloadedStatusModifiable() {
    return myStatus.getModifiable();
  }

  public void onDownloadStatus(DownloadedFile status) {
    myTable.repaintUrl(status.getKeyURL());
  }

  public void onChange() {
    final boolean visible = isVisible();

    for(final JComponent component : myHideComponents) {
      component.setVisible(visible);
    }

    if(visible) {
      final JComponent c = myTable.getComponent();
      c.invalidate();
      final Container p = c.getParent();
      if(p instanceof JComponent) {
        ((JComponent)p).revalidate();
      }
    }
  }

  private boolean isVisible() {
    final AListModel<? extends A> model = myTable.getAComponent().getCollectionModel();
    return model.getSize() > 0;
  }

  protected static abstract class BaseAttachmentAction<A extends Attachment, C extends Collection<A>> extends SimpleAction {
    protected BaseAttachmentAction(@Nullable String name, @Nullable Icon icon) {
      super(name, icon);
      watchRole(AttachmentsEnv.ATTACHMENT);
      watchRole(ItemUiModel.ITEM_WRAPPER);
    }

    protected void customUpdate(UpdateContext context) throws CantPerformException {
      ItemUiModel model = getUIModel(context);
      context.updateOnChange(model);
      ModelKey<C> key = getKey(context);
      update(context, model, key);
    }

    protected void doPerform(ActionContext context) throws CantPerformException {
      perform(context, getUIModel(context), getKey(context));
    }

    protected abstract ModelKey<C> getKey(ActionContext context) throws CantPerformException;

    protected abstract void perform(ActionContext context, ItemUiModel model, ModelKey<C> key)
      throws CantPerformException;

    protected void update(UpdateContext context, ItemUiModel model, ModelKey<C> key)
      throws CantPerformException {}

    private ItemUiModel getUIModel(ActionContext context) throws CantPerformException {
      final ItemWrapper wrapper = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
      return CantPerformException.cast(ItemUiModel.class, wrapper);
    }
  }

  protected static abstract class BaseDeleteAction<A extends Attachment, C extends Collection<A>>
    extends BaseAttachmentAction<A, C>
  {
    protected BaseDeleteAction() {
      super("Delete Attachment", null);
    }

    protected void update(UpdateContext context, ItemUiModel model, ModelKey<C> key)
      throws CantPerformException
    {
      final List<Attachment> selectedAttachments = getDeletable(context);
      final int size = selectedAttachments.size();
      if(size > 1) {
        context.putPresentationProperty(PresentationKey.NAME, "Delete " + size + " Attachments");
      }
    }

    protected void perform(
      ActionContext context, ItemUiModel uiModel, ModelKey<C> key) throws CantPerformException
    {
      final ModelMap model = uiModel.getModelMap();

      final C attachments = (C)Collections15.<A>arrayList();
      attachments.addAll(key.getValue(model));
      attachments.removeAll(getDeletable(context));

      final PropertyMap updated = new PropertyMap();
      key.setValue(updated, attachments);
      key.copyValue(model, updated);
    }

    private List<Attachment> getDeletable(ActionContext context) throws CantPerformException {
      final List<Attachment> attachments = context.getSourceCollection(AttachmentsEnv.ATTACHMENT);
      final List<Attachment> local = Collections15.arrayList();
      for(final Attachment attachment : attachments) {
        if(attachment.isLocal()) {
          local.add(attachment);
        }
      }
      return CantPerformException.ensureNotEmpty(local);
    }
  }
}
