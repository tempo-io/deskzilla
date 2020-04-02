package com.almworks.bugzilla.gui.comments;

import com.almworks.api.actions.CommentEditorDialog;
import com.almworks.api.actions.UploadOnSuccess;
import com.almworks.api.application.DBDataRoles;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.engine.Connection;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.bugzilla.provider.meta.CommentsFactory;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.SyncManager;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.util.L;
import com.almworks.util.config.Configuration;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;

import javax.swing.*;
import java.util.Map;

public class AddCommentAction extends SimpleAction {
  public static final AnAction INSTANCE = new AddCommentAction();
  private static final String ADD_PRIVATE_COMMENT_SETTING = "addPrivateComment";

  public AddCommentAction() {
    super(L.actionName("Comment\u2026"), Icons.ACTION_COMMENT_ADD);
    setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, L.tooltip("Add a comment to the selected bug"));
    setDefaultPresentation(PresentationKey.ENABLE, EnableState.ENABLED);
    watchRole(ItemWrapper.ITEM_WRAPPER);
  }

  @Override
  protected void customUpdate(UpdateContext context) throws CantPerformException {
    DBDataRoles.checkAnyConnectionHasCapability(context, Connection.Capability.EDIT_ITEM);
    ItemWrapper bug = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    DBDataRoles.checkExisting(bug);
    BugzillaConnection conn = CantPerformException.cast(BugzillaConnection.class, bug.getConnection());
    CantPerformException.ensure(conn.hasCapability(Connection.Capability.EDIT_ITEM));
  }

  @Override
  protected void doPerform(ActionContext context) throws CantPerformException {
    String strId = getBugId(context);
    MyHandler handler = MyHandler.create(context);
    CommentEditorDialog dialog = CommentEditorDialog.addComment(context, strId, handler, handler.getCheckBox());
    dialog.getBuilder().showWindow();
  }

  public static String getBugId(ActionContext context) throws CantPerformException {
    ItemWrapper bug = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    DBDataRoles.checkExisting(bug);
    Integer id = bug.getModelKeyValue(BugzillaKeys.id);
    return id != null ? "#" + id : "<New>";
  }

  public static class MyHandler implements CommentEditorDialog.Handler {
    private final SyncManager mySyncManager;
    private final long myBug;
    private final JCheckBox myPrivate;

    private MyHandler(SyncManager syncManager, long bug, JCheckBox isPrivate) {
      mySyncManager = syncManager;
      myBug = bug;
      myPrivate = isPrivate;
    }

    @Override
    public void onCommentEdited(boolean success, final String commentText, boolean commitImmediately,
      CommentEditorDialog dialog)
    {
      if (!success) return;
      AggregatingEditCommit commit = new AggregatingEditCommit();
      if (commitImmediately) commit.addProcedure(null, UploadOnSuccess.create(myBug));
      Map<DBAttribute<?>,Object> values = CommentsFactory.setCommentValues(commentText, myBug);
      if (myPrivate != null) values.put(CommentsLink.attrPrivate, myPrivate.isSelected());
      commit.addCreateCopyConnection(values, myBug);
      mySyncManager.commitEdit(commit);
    }

    public static MyHandler create(ActionContext context) throws CantPerformException {
      ItemWrapper bug = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
      BugzillaConnection connection = CantPerformException.cast(BugzillaConnection.class, bug.getConnection());
      Configuration config = connection.getConnectionConfig("addComment");
      JCheckBox isPrivate = getPrivateCheckbox(connection, config);
      SyncManager syncManager = context.getSourceObject(SyncManager.ROLE);
      return new MyHandler(syncManager, bug.getItem(), isPrivate);
    }

    private static JCheckBox getPrivateCheckbox(BugzillaConnection connection, Configuration config) throws CantPerformExceptionSilently {
      if (connection.getContext().isCommentPrivacyAccessible()) {
        JCheckBox checkBox = BugzillaCommentsUtil.createPrivateCommentCheckbox(false);
        checkBox.setSelected(config.getBooleanSetting(ADD_PRIVATE_COMMENT_SETTING, false));
        return checkBox;
      }
      return null;
    }

    public JCheckBox getCheckBox() {
      return myPrivate;
    }
  }
}
