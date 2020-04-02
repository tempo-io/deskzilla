package com.almworks.bugzilla.gui.comments;

import com.almworks.api.application.ModelMap;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.bugzilla.provider.comments.LoadedCommentKey;
import com.almworks.bugzilla.provider.comments.ResolvedCommentKey;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.actions.*;

import javax.swing.*;

public class BugzillaCommentsUtil {
  static boolean isPrivacySupported(ModelMap map) throws CantPerformExceptionSilently {
    BugzillaContext context = BugzillaUtil.getContext(map);
    if (context == null) {
      assert false : map;
      throw new CantPerformExceptionSilently("" + map);
    }
    return context.isCommentPrivacyAccessible();
  }

  static JCheckBox createPrivateCommentCheckbox(boolean description) {
    JCheckBox privacyCheckbox;
    privacyCheckbox = new JCheckBox();
    NameMnemonic.parseString(description ? "&Private description" : "&Private comment").setToButton(privacyCheckbox);
    return privacyCheckbox;
  }

  public static ResolvedCommentKey getComment(ActionContext context) throws CantPerformException {
    ResolvedCommentKey comment =
      CantPerformException.cast(ResolvedCommentKey.class, context.getSourceObject(LoadedCommentKey.DATA_ROLE));
    if (comment.isFinal()) throw new CantPerformException();
    return comment;
  }

  static String getBugId(ItemVersion comment) {
    ItemVersion bug = comment.readValue(CommentsLink.attrMaster);
    Integer id = bug != null ? bug.getValue(Bug.attrBugID) : null;
    return id != null ? "#" + id : "<New>";
  }
}
