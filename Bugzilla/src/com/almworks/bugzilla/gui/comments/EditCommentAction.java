package com.almworks.bugzilla.gui.comments;

import com.almworks.api.actions.*;
import com.almworks.api.application.DBDataRoles;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.edit.ItemSyncSupport;
import com.almworks.api.edit.WindowItemEditor;
import com.almworks.api.engine.Connection;
import com.almworks.api.gui.BasicWindowBuilder;
import com.almworks.api.gui.DialogManager;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.comments.LoadedCommentKey;
import com.almworks.bugzilla.provider.comments.ResolvedCommentKey;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.AttributeMap;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.L;
import com.almworks.util.commons.FactoryE;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;

import javax.swing.*;

public class EditCommentAction extends SimpleAction {
  public static final EditCommentAction EDIT_COMMENT = new EditCommentAction(false);
  public static final EditCommentAction EDIT_DESCRIPTION = new EditCommentAction(true);

  private final boolean myDescription;

  public EditCommentAction(boolean description) {
    super(L.actionName(description ? "Edit Description" : "Edit Comment"), Icons.ACTION_COMMENT_EDIT);
    myDescription = description;
    setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION,
      description ? "Edit description" : "Edit selected comment");
    setDefaultPresentation(PresentationKey.ENABLE, EnableState.ENABLED);
    watchRole(LoadedCommentKey.DATA_ROLE);
    watchRole(ItemWrapper.ITEM_WRAPPER);
    watchModifiableRole(SyncManager.MODIFIABLE);
  }

  @Override
  protected void customUpdate(UpdateContext context) throws CantPerformException {
    DBDataRoles.checkAnyConnectionHasCapability(context, Connection.Capability.EDIT_ITEM);
    ResolvedCommentKey comment = BugzillaCommentsUtil.getComment(context);
    ItemActionUtils.checkCanEdit(context, comment.getItem(), comment.getConnection());
  }

  @Override
  protected void doPerform(ActionContext context) throws CantPerformException {
    ResolvedCommentKey comment = BugzillaCommentsUtil.getComment(context);
    BugzillaConnection connection =
      ItemActionUtils.getConnection(context, comment.getConnection(), BugzillaConnection.class);

    EditControl control = ItemSyncSupport.prepareEditOrShowLockingEditor(context, comment);
    boolean privacyAccessible = connection.getContext().isCommentPrivacyAccessible();
    DialogManager dm = context.getSourceObject(DialogManager.ROLE);
    control.start(new MyHandler(control, comment.getItem(), myDescription, privacyAccessible, comment.getIndex(), dm));
  }

  private static class MyHandler implements EditorFactory, CommentEditorDialog.Handler {
    private final boolean myDescription;
    private final EditControl myControl;
    private final long myCommentItem;
    private final int myIndex;
    private final DialogManager myManager;
    private final JCheckBox myPrivacyCheckbox;

    private MyHandler(EditControl control, long commentItem, boolean description, boolean privacyAccessible, int index,
      DialogManager manager) {
      myControl = control;
      myCommentItem = commentItem;
      myDescription = description;
      myIndex = index;
      myManager = manager;
      if (privacyAccessible) {
        myPrivacyCheckbox = BugzillaCommentsUtil.createPrivateCommentCheckbox(myDescription);
      } else myPrivacyCheckbox = null;
    }

    @Override
    public void onCommentEdited(boolean success, String commentText, boolean commitImmediately, CommentEditorDialog dialog) {
      if (!success) return;
      AggregatingEditCommit commit = new AggregatingEditCommit();
      AttributeMap values = new AttributeMap();
      values.put(CommentsLink.attrText, commentText);
      if (myPrivacyCheckbox != null)
        values.put(CommentsLink.attrPrivate, myPrivacyCheckbox.isSelected());
      if (commitImmediately)
        commit.addProcedure(null, UploadOnSuccess.createForMaster(myCommentItem, CommentsLink.attrMaster));
      commit.updateValues(myCommentItem, values);
      myControl.commit(commit);
    }

    @Override
    public ItemEditor prepareEdit(DBReader reader, EditPrepare prepare) {
      final ItemVersion comment = SyncUtils.readTrunk(reader, myCommentItem);
      final Long connection = comment.getValue(SyncAttributes.CONNECTION);
      if (connection == null) return null;
      final Boolean isPrivate = comment.getValue(CommentsLink.attrPrivate);
      final String text = comment.getValue(CommentsLink.attrText);
      final String strId = BugzillaCommentsUtil.getBugId(comment);
      return new WindowItemEditor(prepare, new FactoryE<BasicWindowBuilder, CantPerformException>() {
        @Override
        public BasicWindowBuilder create() throws CantPerformException {
          CommentEditorDialog dialog;
          if (myPrivacyCheckbox != null) myPrivacyCheckbox.setSelected(isPrivate != null && isPrivate);
          if (myDescription) {
            dialog = CommentEditorDialog.editCommentWithTitle("Edit Description", text, MyHandler.this,
              myPrivacyCheckbox, myManager);
          } else {
            dialog = CommentEditorDialog.editComment(strId, "" + myIndex, text, MyHandler.this, myPrivacyCheckbox, myManager);
          }
          return dialog.getBuilder();
        }
      });
    }
  }
}
