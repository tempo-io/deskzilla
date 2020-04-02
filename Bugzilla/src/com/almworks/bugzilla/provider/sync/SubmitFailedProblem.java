package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.ConnectorException;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.spi.provider.AbstractItemProblem;
import com.almworks.util.L;
import com.almworks.util.Pair;

/**
 * :todoc:
 *
 * @author sereda
 */
public class SubmitFailedProblem extends AbstractItemProblem {
  private final ConnectorException myException;

  public SubmitFailedProblem(long item, Integer bugId, long timeCreated, ConnectorException exception, BugzillaContext context,
    Pair<String, Boolean> credentialState) {
    super(item, bugId != null ? String.valueOf(bugId) : null, timeCreated, context, credentialState);
    myException = exception;
  }

  public String getLongDescription() {
    // todo
    return myException != null ? myException.getLongDescription() : L.content("Submit failed.");
  }

  public String getShortDescription() {
    return myException != null ? myException.getShortDescription() : L.content("Submit failed.");
  }

  public Cause getCause() {
    return Cause.GENERIC_UPLOAD_FAILURE;
  }
}
