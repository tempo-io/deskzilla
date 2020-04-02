package com.almworks.bugzilla.provider.sync;

import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.spi.provider.AbstractItemProblem;
import com.almworks.util.*;
import org.almworks.util.Util;

/**
 * :todoc:
 *
 * @author sereda
 */
public class UpdateFailedProblem extends AbstractItemProblem {
  private final Integer myBugID;
  private final String myFailedAttribute;

  public UpdateFailedProblem(long item, Integer bugID, long timeCreated, BugzillaContext context, String failedAttribute, Pair<String, Boolean> credentialState) {
    super(item, bugID != null ? String.valueOf(bugID) : null, timeCreated, context, credentialState);
    assert bugID != null;
    assert failedAttribute != null;
    myBugID = bugID;
    myFailedAttribute = failedAttribute;
  }

  public String getLongDescription() {
    return L.content("Bug upload failed for bug #" + myBugID + "\n" +
      "Failed to upload " + Util.upper(myFailedAttribute) + "\n\n" +
      "This means that we've attempted to upload the bug to the Bugzilla server, " +
      "and Bugzilla didn't give any error (or we were unable to detect it), " +
      "but (partly) our update request was ignored.\n\n" +
      "Please check that your Bugzilla supports this attribute and that you have " +
      "permissions to update problematic attribute.");
  }

  public Cause getCause() {
    return Cause.GENERIC_UPLOAD_FAILURE;
  }

  public String getShortDescription() {
    return L.tooltip("Upload failed [Bug " + myBugID + "][" + English.capitalize(myFailedAttribute) + "]");
  }
}
