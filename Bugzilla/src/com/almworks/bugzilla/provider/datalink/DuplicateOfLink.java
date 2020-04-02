package com.almworks.bugzilla.provider.datalink;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBFilter;
import com.almworks.items.sync.ItemProxy;
import org.almworks.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DuplicateOfLink extends SingleReferenceLink<Integer> {
  // todo move to the integration package
  private static final Pattern DUPLICATE_COMMENT_PATTERN =
    Pattern.compile(".*\\*{3}.*bug.*duplicate\\D*(\\d+).*\\*{3}", Pattern.CASE_INSENSITIVE);

  public DuplicateOfLink(DBAttribute<Long> attribute)
  {
    super(attribute, BugzillaAttribute.DUPLICATE_OF,
      Bug.typeBug, Bug.attrBugID, Bug.attrBugID,
      true, null, false);
  }

//  public Object getPrototypeValue(Workspace workspace, PrivateMetadata privateMetadata) {
//    return null;
//  }
//
  @Override
  public String getRemoteString(BugInfo bugInfo, PrivateMetadata privateMetadata) {
    // move getting "duplicate" status to the integration package
    String value = bugInfo.getValues().getScalarValue(BugzillaAttribute.RESOLUTION, null);
    if (!BugzillaAttribute.RESOLUTION_DUPLICATE.equalsIgnoreCase(value))
      return null;
    Comment[] comments = bugInfo.getOrderedComments();
    for (int i = comments.length - 1; i >= 0; i--) {
      String comment = comments[i].getText().replaceAll("\n", "");
      Matcher matcher = DUPLICATE_COMMENT_PATTERN.matcher(comment);
      if (matcher.matches()) {
        String idString = matcher.group(1);
        if (idString == null)
          continue;
        try {
          int id = Integer.parseInt(idString);
          if (id > 0)
            return idString;
        } catch (NumberFormatException e) {
          // fall through to next comment
        }
      }
    }
    Log.warn(this + ": bug in duplicate status, bug duplicate bug id was not found (" + bugInfo + ")");
    return null;
  }

  @Override
  public DBFilter getReferentsView(PrivateMetadata pm) {
    return pm.getBugsView();
  }

  @Override
  public ItemProxy createProxy(PrivateMetadata pm, Integer key) {
    return Bug.createBugProxy(pm.thisConnectionItem(), key);
  }

  @Override
  public boolean cannotTell(BugInfo bugInfo) {
    return false;
  }

  @Override
  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    // @see WorkflowLink
    return null;
  }

//  public void initializePrototype(RevisionCreator creator, Workspace workspace, PrivateMetadata privateMetadata) {
//  }

  @Override
  public String toString(Integer integer) {
    return integer == null ? null : integer.toString();
  }

  @Override
  public Integer fromString(String s) {
    if(s == null) {
      return null;
    }
    try {
      return Integer.valueOf(s);
    } catch(NumberFormatException e) {
      Log.warn(s, e);
      return null;
    }
  }
}
