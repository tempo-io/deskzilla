package com.almworks.bugzilla.provider.sync;

import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.spi.provider.AbstractItemProblem;
import com.almworks.util.L;
import com.almworks.util.Pair;
import org.almworks.util.Util;

/**
 * :todoc:
 *
 * @author sereda
 */
public class UpdateImpossibleProblem extends AbstractItemProblem {
  private final Integer myBugID;
  private final String myCause;

  public UpdateImpossibleProblem(long item, Integer bugID, long timeCreated, BugzillaContext context, String cause, Pair<String, Boolean> credentialState) {
    super(item, Util.stringOrNull(bugID), timeCreated, context, credentialState);
    assert cause != null;
    assert bugID != null;
    myBugID = bugID;
    myCause = cause;
  }

  public String getLongDescription() {
    return L.content("Bug upload failed for bug " + myBugID + "\n" +
      Util.upper(myCause) + "\n\n" +
      "Current changes could not be uploaded to Bugzilla.\n\n" +
      "Please review bug changes and make sure they are compatible with Bugzilla work flow rules.");
  }

  public String getShortDescription() {
    return L.content("Upload impossible [Bug " + myBugID + "]");
  }

  public Cause getCause() {
    return Cause.GENERIC_UPLOAD_FAILURE;
  }
}
