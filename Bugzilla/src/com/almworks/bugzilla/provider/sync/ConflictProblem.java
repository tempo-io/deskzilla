package com.almworks.bugzilla.provider.sync;

import com.almworks.api.application.DBDataRoles;
import com.almworks.api.gui.MainMenu;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.spi.provider.AbstractItemProblem;
import com.almworks.util.L;
import com.almworks.util.Pair;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Util;

public class ConflictProblem extends AbstractItemProblem {
  private final Integer myBugID;
  private final String myDiagnostics;

  public ConflictProblem(long item, Integer bugID, long timeCreated, BugzillaContext context, Pair<String, Boolean> credentialState, String diagnostics) {
    super(item, Util.stringOrNull(bugID), timeCreated, context, credentialState);
    myDiagnostics = diagnostics;
    assert bugID != null;
    myBugID = bugID;
  }

  public String getLongDescription() {
    return L.content("There was a conflict during upload of bug " + myBugID + ".\n" +
      "You have changed this bug, and at the same time somebody changed it" +
      " on the server. So now you need to merge two conflicting versions of the bug. Please retry " +
      "upload after that.\n\n Details:\n " + myDiagnostics);
  }

  public String getShortDescription() {
    return L.content("Upload conflict [Bug #" + myBugID + "]");
  }

  public Cause getCause() {
    return Cause.UPLOAD_CONFLICT;
  }

  public boolean isResolvable() {
    return true;
  }

  public void resolve(ActionContext context) throws CantPerformException {
    ActionRegistry registry = context.getSourceObject(ActionRegistry.ROLE);
    AnAction action = registry.getAction(MainMenu.Edit.MERGE);
    assert action != null;
    try {
      action.perform(context.childContext(ConstProvider.singleData(DBDataRoles.ITEM_ROLE, myItem)));
    } catch (CantPerformException e) {
      // ignore?
    }
  }
}
