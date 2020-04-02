package com.almworks.api.actions;

import com.almworks.api.application.ItemWrapper;
import com.almworks.api.engine.Connection;
import com.almworks.api.gui.*;
import com.almworks.edit.EditLifecycleImpl;
import com.almworks.engine.gui.attachments.AttachmentChooserOpen;
import com.almworks.util.Terms;
import com.almworks.util.config.Configuration;
import com.almworks.util.images.Icons;
import com.almworks.util.properties.Role;
import com.almworks.util.ui.UIComponentWrapper;
import com.almworks.util.ui.actions.*;
import org.jetbrains.annotations.*;
import util.concurrent.SynchronizedBoolean;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

public abstract class BaseAttachFileAction<C extends UIComponentWrapper> extends SimpleAction {
  public final static Role<File> ATTACHMENTS = Role.role("attachments", File.class);

  private final SynchronizedBoolean myUploadImmediately = new SynchronizedBoolean(true);
  private final boolean myMultipleAllowed;
  private final int myMaxFileLength;

  public BaseAttachFileAction(boolean multipleAllowed, int maxFileLength) {
    super(multipleAllowed ? "Attach Files\u2026" : "Attach File\u2026", Icons.ACTION_ATTACH_FILE);
    watchRole(ItemWrapper.ITEM_WRAPPER);
    myMultipleAllowed = multipleAllowed;
    myMaxFileLength = maxFileLength;
    setDefaultText(PresentationKey.SHORT_DESCRIPTION,
      "Attach " + (multipleAllowed ? "files" : "a file") + " to the selected " + Terms.ref_artifact);
  }

  protected boolean getUploadImmediately() {
    return myUploadImmediately.get();
  }

  protected abstract SimpleAction createAttachAction(C editor, ItemWrapper masterItem, SynchronizedBoolean uploadImmediately);

  protected abstract C createEditor(Configuration config, File[] attachment, ItemWrapper item) throws CantPerformException;

  public static File[] askForAttachments(Component component, Configuration config, boolean multipleAllowed, int maxFileLength) {
    File[] attachments;
    if (multipleAllowed) {
      attachments = AttachmentChooserOpen.showForMultiple(component, maxFileLength, config);
      if (attachments == null || attachments.length == 0)
        return null;
    } else {
      File attachment = AttachmentChooserOpen.show(component, null, maxFileLength, config);
      if (attachment == null)
        return null;
      attachments = new File[] {attachment};
    }
    return attachments;
  }

  @Override
  protected void customUpdate(UpdateContext context) throws CantPerformException {
    List<ItemWrapper> primaryItems = ItemActionUtils.basicUpdate(context);
    ItemWrapper item = CantPerformException.ensureSingleElement(primaryItems);
    // basicUpdate checks that item has connection
    //noinspection ConstantConditions
    CantPerformException.ensure(item.getConnection().hasCapability(Connection.Capability.EDIT_ITEM));
  }

  @Override
  protected void doPerform(ActionContext context) throws CantPerformException {
    ItemWrapper item = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    BasicWindowBuilder builder = CantPerformException.ensureNotNull(createWindow(context, item));
    EditLifecycleImpl.create(builder, null);
    builder.showWindow();
  }

  private BasicWindowBuilder createWindow(@NotNull ActionContext context, ItemWrapper item) throws CantPerformException {
    List<File> externAttachments = getExternalAttachments(context);
    DialogBuilder builder = context.getSourceObject(DialogManager.ROLE).createBuilder("AttachFile");
    final Configuration config = builder.getConfiguration().getOrCreateSubset("AttachEditor");
    File[] attachments = externAttachments == null ? askForAttachments(context, config) : getExternAttachments(externAttachments);
    if (attachments == null)
      return null;

    final C editor = createEditor(config, attachments, item);
    builder.setTitle(getTitle(item));
    builder.setModal(false);
    builder.setContent(editor);
    builder.setEmptyCancelAction();
    // todo :refactoring: bug here: if two editors are opened, they share state of myUploadImmediately, but their checkboxes do not. Remove checkbox and do like in other places -- toolbar with "Upload", "Save draft" etc
    JCheckBox checkbox = CommentEditorDialog.createCommitImmediatelyCheckbox(builder.getConfiguration(), myUploadImmediately);
    builder.setBottomLineComponent(checkbox);
    builder.setOkAction(createAttachAction(editor, item, myUploadImmediately));
    return builder;
  }

  @Nullable
  private static List<File> getExternalAttachments(ActionContext context) {
    try {
      return context.getSourceCollection(ATTACHMENTS);
    } catch (CantPerformException e) {
      // no external attachments is ok
      return null;
    }
  }

  private File[] getExternAttachments(List<File> externAttachments) {
    if (externAttachments.size() == 0)
      return null;
    File[] attachments;
    if (myMultipleAllowed) {
      attachments = externAttachments.toArray(new File[externAttachments.size()]);
    } else {
      attachments = new File[] {externAttachments.get(0)};
    }
    return attachments;
  }

  private File[] askForAttachments(ActionContext context, Configuration config) {
    return askForAttachments(context.getComponent(), config, myMultipleAllowed, myMaxFileLength);
  }

  private String getTitle(ItemWrapper item) {
    String title = myMultipleAllowed ? "Attach Files" : "Attach File";
    final Connection c = item.getConnection();
    if (c != null) {
      String itemId = c.getDisplayableItemId(item);
      if (itemId != null) {
        title = title + " to " + itemId;
      }
    }
    return title;
  }
}
