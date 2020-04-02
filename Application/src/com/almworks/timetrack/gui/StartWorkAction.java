package com.almworks.timetrack.gui;

import com.almworks.api.application.DBDataRoles;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.gui.MainMenu;
import com.almworks.timetrack.api.*;
import com.almworks.util.Terms;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;

public class StartWorkAction extends SimpleAction {
  public StartWorkAction() {
    super((String)null, Icons.START_WORK_ACTION);
    setDefaultText(PresentationKey.NAME, "&Work on " + Terms.ref_Artifact);
    setDefaultText(PresentationKey.SHORT_DESCRIPTION,
      "Start tracking time spent on the selected " + Terms.ref_artifact);
    watchRole(TimeTracker.TIME_TRACKER);
    watchRole(ItemWrapper.ITEM_WRAPPER);
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    final ItemWrapper item = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    final TimeTracker tt = context.getSourceObject(TimeTracker.TIME_TRACKER);
    context.updateOnChange(tt.getModifiable());
    DBDataRoles.checkExisting(item);
    if(tt.isTracking() && tt.isCurrentTaskForItem(item.getItem())) {
      context.setEnabled(false);
      return;
    }

    final TimeTrackingCustomizer ttc = context.getSourceObject(TimeTrackingCustomizer.ROLE);
    final boolean perm = ttc.isTimeTrackingPermissionGranted(item);
    context.setEnabled(perm ? EnableState.ENABLED : EnableState.INVISIBLE);

    IdActionProxy.setShortcut(context, MainMenu.Tools.TIME_TRACKING_START_WORK_ON_ISSUE);
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    final ItemWrapper item = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    DBDataRoles.checkExisting(item);
    final TimeTracker tt = context.getSourceObject(TimeTracker.TIME_TRACKER);
    final TimeTrackerTask task = new TimeTrackerTask(item.getItem());
    tt.setTrackingAndCurrentTask(true, task);

    // todo maybe just show for the first time
    context.getSourceObject(TimeTrackerWindow.ROLE).show();
  }
}
