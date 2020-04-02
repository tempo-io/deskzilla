package com.almworks.bugzilla.provider;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.Comment;
import com.almworks.api.application.viewer.CommentsController;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.provider.comments.LoadedCommentKey;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.engine.gui.CommentsFormlet;
import com.almworks.util.config.ConfigurationException;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.*;
import com.almworks.util.ui.swing.SwingTreeUtil;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

class ShowCommentAction extends SimpleAction {
  private static final int ACTION_VIEW = 0;
  private static final int ACTION_COPY_URL = 1;
  private final int myAction;
  private final int myCommentId;
  private final int myBugId;

  public ShowCommentAction(int bugId, int commentId, int action) {
    myCommentId = commentId;
    myBugId = bugId;
    myAction = action;
    String name;
    switch (action) {
    case ACTION_VIEW: name = "View Comment"; break;
    case ACTION_COPY_URL: name = "Copy URL"; break;
    default: assert false; name = "";
    }
    setDefaultPresentation(PresentationKey.NAME, name);
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    if (myBugId < 0) {
      CommentsController<?> controller = context.getSourceObject(CommentsController.ROLE);
      context.setEnabled(commentIndex(controller) >= 0 ? EnableState.ENABLED : EnableState.INVISIBLE);
    } else context.setEnabled(true);
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    switch (myAction) {
    case ACTION_VIEW: viewComment(context); break;
    case ACTION_COPY_URL: copyUrl(context); break;
    default: assert false : myAction;
    }
  }

  private void copyUrl(ActionContext context) throws CantPerformException {
    UIUtil.copyToClipboard(getUrl(context));
  }

  private String getUrl(ActionContext context) throws CantPerformException {
    ItemWrapper item = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    String bugUrl;
    if (myBugId < 0) bugUrl = CantPerformException.ensureNotNull(item.getItemUrl());
    else {
      BugzillaConnection connection = CantPerformException.ensureNotNull(item.services().getConnection(BugzillaConnection.class));
      String baseUrl;
      try {
        ScalarModel<OurConfiguration> configModel = connection.getContext().getConfiguration();
        if (!configModel.isContentKnown()) throw new CantPerformException();
        baseUrl = configModel.getValue().getBaseURL();
      } catch (ConfigurationException e) {
        throw new CantPerformException(e);
      }
      bugUrl = BugzillaIntegration.getBugUrl(baseUrl, myBugId);
    }
    return bugUrl + "#c" + myCommentId;
  }

  private void viewComment(ActionContext context) throws CantPerformException {
    if (myBugId < 0)
      selectComment(context.getSourceObject(CommentsController.ROLE));
    else {
      SearchResult result = BugReferenceParser.performTextSearch(String.valueOf(myBugId), context);
      if (result == SearchResult.EMPTY) throw new CantPerformException();
      result.addListener(Lifespan.FOREVER, ThreadGate.AWT, new SearchListener() {
        public void onSearchCompleted(SearchResult result) {
          findBugAndShowComment(BugzillaKeys.id, result);
        }

        public void onSearchClosed(SearchResult result) {

        }
      });
    }
  }

  private void findBugAndShowComment(ModelKey<Integer> idKey, SearchResult result) {
    ItemWrapper bug = findBug(idKey, result.getItems());
    if (bug == null) return;
    result.showItem(bug);
    JComponent viewer = result.getViewer();
    CommentsController comments = findComments(viewer);
    if (comments == null) return;
    selectComment(comments);
    comments.requestFocus();
  }

  private ItemWrapper findBug(ModelKey<Integer> idKey, Collection<? extends ItemWrapper> wrappers) {
    for (ItemWrapper wrapper : wrappers) {
      Integer id = wrapper.getModelKeyValue(idKey);
      if (id != null && id == myBugId) return wrapper;
    }
    return null;
  }

  private void selectComment(CommentsController<?> controller) {
    controller.scrollToVisible(commentIndex(controller));
  }

  private CommentsController findComments(JComponent component) {
    if (component == null) return null;
    java.util.List<Component> descendants = SwingTreeUtil.descendants(component);
    for (Component descendant : descendants) {
      if (!(descendant instanceof JComponent)) continue;
      CommentsController controller = CommentsFormlet.COMMENTS_CONTROLLER.getClientValue((JComponent) descendant);
      if (controller != null) return controller;
    }
    return null;
  }

  private final int commentIndex(CommentsController<?> controller) {
    java.util.List<? extends Comment> comments = controller.getComments();
    for (int i = 0; i < comments.size(); i++) {
      Comment comment = comments.get(i);
      if (!(comment instanceof LoadedCommentKey)) continue;
      if (myCommentId == ((LoadedCommentKey) comment).getIndex()) return i;
    }
    return -1;
  }

  public static AnAction view(int bugId, int commentId) {
    return new ShowCommentAction(bugId, commentId, ACTION_VIEW);
  }

  public static AnAction copyUrl(int bugId, int commentId) {
    return new ShowCommentAction(bugId, commentId, ACTION_COPY_URL);
  }
}
