package com.almworks.bugzilla.provider.sync;

import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.spi.provider.AbstractItemProblem;
import com.almworks.util.Pair;
import org.almworks.util.Util;

/**
 * @author Vasya
 */
public class InaccessibleItemProblem extends AbstractItemProblem {
  private final BugInfo.ErrorType myErrorType;

  public InaccessibleItemProblem(long item, Integer bugId, long timeCreated, BugzillaContext context, BugInfo.ErrorType errorType, Pair<String, Boolean> credentialState) {
    super(item, Util.stringOrNull(bugId), timeCreated, context, credentialState);
    assert errorType != null: item;
    myErrorType = errorType;
  }

  public String getShortDescription() {
    return "Cannot access bug";
  }

  public String getLongDescription() {
    return "Cannot access bug: " + getErrorMessage(myErrorType) + ".";
  }

  public Cause getCause() {
    if (myErrorType == BugInfo.ErrorType.BUG_NOT_FOUND) return Cause.REMOTE_NOT_FOUND;
    else if (myErrorType == BugInfo.ErrorType.BUG_ACCESS_DENIED) return Cause.ACCESS_DENIED;
    return null;
  }

  private static String getErrorMessage(BugInfo.ErrorType errorType) {
    if (errorType == BugInfo.ErrorType.BUG_ACCESS_DENIED)
      return "access denied";
    else if (errorType == BugInfo.ErrorType.BUG_NOT_FOUND)
      return "bug is not found";
    return "Unknown problem";
  }

  public boolean isCauseForRemoval() {
    return myErrorType == BugInfo.ErrorType.BUG_NOT_FOUND;
  }

  public boolean isSerious() {
    return myErrorType == BugInfo.ErrorType.BUG_NOT_FOUND;
  }
}
