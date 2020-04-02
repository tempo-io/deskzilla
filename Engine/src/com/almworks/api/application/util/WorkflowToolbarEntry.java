package com.almworks.api.application.util;

import com.almworks.api.application.WorkflowComponent2;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.FilteringConvertingListDecorator;
import com.almworks.util.collections.Convertor;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.AToolbar;
import com.almworks.util.components.DropDownButton;
import com.almworks.util.config.Configuration;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;

public class WorkflowToolbarEntry implements ToolbarEntry {
  private static final String SETTING_WORKWLOW_ACTION = "wfAction";
  private final Configuration myConfig;
  private final WorkflowComponent2 myWorkflow;

  public WorkflowToolbarEntry(Configuration config, WorkflowComponent2 workflow) {
    myConfig = config;
    myWorkflow = workflow;
  }

  public void addToToolbar(AToolbar toolbar) {
    DropDownButton button = toolbar.addDropDownButton(null, "Workflow");
    setupButton(button);
  }

  public void addToPanel(JPanel panel, JComponent contextComponent) {
    panel.add(AToolbar.createDropDownButton(contextComponent, "Workflow"));
  }

  private void setupButton(DropDownButton button) {
    AListModel<IdentifiableAction> workflowActions = myWorkflow.getWorkflowActions();
    AListModel<AnAction> actions = FilteringConvertingListDecorator.create(Lifespan.FOREVER, workflowActions,
      Condition.<IdentifiableAction>always(), new Convertor<IdentifiableAction, AnAction>() {
        @Override
        public AnAction convert(final IdentifiableAction action) {
          return new AnActionDelegator(action) {
            @Override
            public void perform(ActionContext context) throws CantPerformException {
              myConfig.setSetting(SETTING_WORKWLOW_ACTION, getStringIdentity(action));
              super.perform(context);
            }
          };
        }
      });
    String actionId = myConfig.getSetting(SETTING_WORKWLOW_ACTION, "");
    int index = findActionIndex(workflowActions, actionId);
    AnAction selection = index >= 0 ? actions.getAt(index) : null;
    button.setActions(actions, selection);
    button.setAutoHideShow(true);
  }

  private int findActionIndex(AListModel<IdentifiableAction> actions, String actionId) {
    AnAction candidate = null;
    int index = -1;
    for (int i = 0; i < actions.getSize(); i++) {
      IdentifiableAction action = actions.getAt(i);
      String id = getStringIdentity(action);
      if (actionId.equals(id)) {
        if (candidate == null) {
          candidate = action;
          index = i;
        }
        else Log.warn("Duplicated action identity: " + actionId + " " + candidate + " " + action);
      }
    }
    return index;
  }

  private static String getStringIdentity(IdentifiableAction action) {
    if (action == null) return null;
    Object identity = action.getIdentity();
    if (identity == null) return "<null>";
    return identity.toString();
  }
}
