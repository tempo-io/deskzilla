package com.almworks.bugzilla.gui.comments;

import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.ModelKey;
import com.almworks.bugzilla.gui.BugzillaFormUtils;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.bugzilla.provider.comments.LoadedCommentKey;
import com.almworks.bugzilla.provider.comments.LocalLoadedComment;
import com.almworks.util.L;
import com.almworks.util.config.ConfigurationException;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;

import java.net.MalformedURLException;

public class OpenCommentInBrowserAction extends SimpleAction {
  private final ModelKey<Integer> myIdKey;

  public OpenCommentInBrowserAction(ModelKey<Integer> idKey) {
    super(L.actionName("Open Comment in Browser"), Icons.ACTION_OPEN_IN_BROWSER);
    myIdKey = idKey;
    if(myIdKey != null) {
      watchRole(ItemWrapper.ITEM_WRAPPER);
      watchRole(LoadedCommentKey.DATA_ROLE);
    }
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    if(myIdKey == null) {
      context.setEnabled(false);
      return;
    }

    final ItemWrapper wrapper = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    if(wrapper.getLastDBValues() == null) {
      context.setEnabled(false);
      return;
    }

    final LoadedCommentKey comment = context.getSourceObject(LoadedCommentKey.DATA_ROLE);
    if(comment instanceof LocalLoadedComment) {
      context.setEnabled(false);
      return;
    }

    context.setEnabled(true);
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    if (myIdKey == null) {
      return;
    }
    
    try {
      LoadedCommentKey comment = context.getSourceObject(LoadedCommentKey.DATA_ROLE);
      int commentIndex = comment.getIndex();
      ItemWrapper itemWrapper = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
      Integer bugId = myIdKey.getValue(itemWrapper.getLastDBValues());
      if (bugId == null)
        return;
//          ActionContext actionContext = new DefaultActionContext(getComponent());
      BugzillaContext bzContext = BugzillaUtil.getContext(itemWrapper);
      assert bzContext != null;
      String baseURL = bzContext.getConfiguration().getValue().getBaseURL();
      baseURL = BugzillaIntegration.normalizeURL(baseURL);
      BugzillaFormUtils.openComment(baseURL, bugId, commentIndex);
    } catch (ConfigurationException e) {
      //ignore
    } catch (MalformedURLException e) {
      //ignore
    }
  }
}
