package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.ConnectorException;
import com.almworks.bugzilla.integration.err.UploadException;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.spi.provider.AbstractItemProblem;
import com.almworks.util.Pair;
import org.almworks.util.Util;

/**
 * :todoc:
 *
 * @author sereda
 */
public class BugzillaExceptionProblem extends AbstractItemProblem {
  protected final ConnectorException myException;
  private final Cause myCause;

  public BugzillaExceptionProblem(long item, Integer bugId, long timeCreated, BugzillaContext context, ConnectorException exception, Pair<String, Boolean> credentialState) {
    super(item, Util.stringOrNull(bugId), timeCreated, context, credentialState);
    assert exception != null;
    myException = exception;
    if (myException instanceof UploadException) {
      myCause = Cause.GENERIC_UPLOAD_FAILURE;
    } else {
      myCause = null;
    }
  }

  public String getLongDescription() {
    return myException.getLongDescription();
  }

  public String getShortDescription() {
    return myException.getShortDescription();
  }

  public Cause getCause() {
    return myCause;
  }
}
