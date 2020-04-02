package com.almworks.bugzilla.gui.comments;

import com.almworks.api.actions.InstantToggleSupport;
import com.almworks.api.actions.ItemActionUtils;
import com.almworks.api.application.LoadedItem;
import com.almworks.api.engine.Connection;
import com.almworks.api.gui.DialogManager;
import com.almworks.api.gui.ShowOnceMessageBuilder;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.bugzilla.provider.comments.LoadedCommentKey;
import com.almworks.bugzilla.provider.comments.ResolvedCommentKey;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.integers.LongArray;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.sync.*;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Util;

import javax.swing.*;

public class ChangeCommentPrivacyAction extends SimpleAction {
  private final boolean myToggling;
  private final InstantToggleSupport myInstantToggle = new InstantToggleSupport("commentPrivacy");

  public ChangeCommentPrivacyAction(boolean toggling) {
    super("Change Comment Privacy", null);
    myToggling = toggling;
    setDefaultPresentation(PresentationKey.ENABLE, EnableState.INVISIBLE);
    watchModifiableRole(SyncManager.MODIFIABLE);
    if (toggling) updateOnChange(myInstantToggle.getModifiable());
  }

  @Override
  protected void customUpdate(UpdateContext context) throws CantPerformException {
    ItemActionUtils.basicUpdate(context);
    final LoadedItem item = context.getSourceObject(LoadedItem.LOADED_ITEM);
    final Connection connection = item.getConnection();
    CantPerformException.ensure(connection instanceof BugzillaConnection);
    //noinspection ConstantConditions
    CantPerformException.ensure(connection.hasCapability(Connection.Capability.EDIT_ITEM));

    final BugzillaContext bugzillaContext = ((BugzillaConnection) connection).getContext();
    CantPerformException.ensure(bugzillaContext.isCommentPrivacyAccessible());

    context.putPresentationProperty(PresentationKey.NAME, "Change Privacy");

    context.watchRole(LoadedCommentKey.DATA_ROLE);
    final LoadedCommentKey comment = context.getSourceObject(LoadedCommentKey.DATA_ROLE);
    CantPerformException.ensure(comment instanceof ResolvedCommentKey);
    ItemActionUtils.checkNotLocked(context, ((ResolvedCommentKey)comment).getItem());

    final Boolean privacy = CantPerformException.ensureNotNull(comment.isPrivate());
    final String name;
    final Icon icon;
    final boolean toggled;
    if (myToggling) {
      toggled = Util.NN(myInstantToggle.getState(context), privacy);
      name = privacy ? "Private" : "Make Private";
      icon = Icons.PRIVATE;
    } else {
      toggled = false;
      name = privacy ? "Make Not Private" : "Make Private";
      icon = privacy ? Icons.NOT_PRIVATE : Icons.PRIVATE;
    }

    context.putPresentationProperty(PresentationKey.NAME, name);
    context.putPresentationProperty(PresentationKey.SMALL_ICON, icon);
    context.putPresentationProperty(PresentationKey.TOGGLED_ON, toggled);
    context.setEnabled(EnableState.ENABLED);
  }

  @Override
  protected void doPerform(ActionContext context) throws CantPerformException {
    final LoadedCommentKey comment = context.getSourceObject(LoadedCommentKey.DATA_ROLE);
    final long item = (CantPerformException.cast(ResolvedCommentKey.class, comment)).getItem();
    final SyncManager syncMan = context.getSourceObject(SyncManager.ROLE);
    CantPerformException.ensure(syncMan.findLock(item) == null);

    final Boolean privacy = CantPerformException.ensureNotNull(comment.isPrivate());
    final DialogManager dialogManager = context.getSourceObject(DialogManager.ROLE);
    final boolean started = syncMan.commitEdit(LongArray.singleton(item), new EditCommit() {
      @Override
      public void performCommit(EditDrain drain) throws DBOperationCancelledException {
        drain.changeItem(item).setValue(CommentsLink.attrPrivate, !privacy);
      }

      @Override
      public void onCommitFinished(boolean success) {
        if (success) {
          ThreadGate.AWT.execute(new Runnable() {
            public void run() {
              final ShowOnceMessageBuilder builder = dialogManager.createOnceMessageBuilder("changePrivacyNotification");
              builder.setTitle(L.frame(Local.parse("Upload Pending")));
              builder.setMessage(
                "<html>You have changed comment privacy mode locally.<br>" +
                "To upload changes, right click on the " + Local.parse(Terms.ref_artifact) + " and select <b>Upload</b>.",
                JOptionPane.INFORMATION_MESSAGE);
              builder.showMessage();
            }
          });
        }
      }
    });
    CantPerformException.ensure(started);

    if (myToggling) myInstantToggle.setState(context, !privacy);
  }
}
