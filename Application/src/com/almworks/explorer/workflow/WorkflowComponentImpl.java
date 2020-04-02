package com.almworks.explorer.workflow;

import com.almworks.api.application.WorkflowComponent2;
import com.almworks.api.application.qb.FilterNode;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.explorer.WorkflowComponent;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.misc.WorkAreaUtil;
import com.almworks.util.advmodel.FixedListModel;
import com.almworks.util.config.ConfigurationException;
import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.ui.actions.IdentifiableAction;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifespan;
import org.picocontainer.Startable;

import java.util.List;
import java.util.Map;

/**
 * @author dyoma
 */
public class WorkflowComponentImpl implements WorkflowComponent, Startable {
  static final String NAME = "name";
  static final String FILTER = "filter";
  static final String CONDITION = "condition";

  private final List<IdentifiableAction> myActions = Collections15.arrayList();
  private final WorkArea myWorkArea;
  private final WorkflowComponent2 myWorkflowComponent2;
  private final CustomEditPrimitiveFactory myCustomFactory;

  public WorkflowComponentImpl(WorkArea workArea, WorkflowComponent2 workflowComponent2, ComponentContainer container) {
    myWorkArea = workArea;
    myWorkflowComponent2 = workflowComponent2;
    myCustomFactory = container.getActor(CustomEditPrimitiveFactory.class);
  }

  private void registerWorkflow(ReadonlyConfiguration config) throws ConfigurationException {
    Map<String, FilterNode> filters = WorkflowLoadUtils.loadStates(config);
    List<WorkflowTransition> transitions = WorkflowLoadUtils.loadTransitions(config, filters, myCustomFactory);

    synchronized (myActions) {
      for (WorkflowTransition transition : transitions) {
        myActions.add(new WorkflowTransitionAction(transition));
      }
    }

    ThreadGate.AWT.execute(new Runnable() {
      @Override
      public void run() {
        myWorkflowComponent2.addWorkflowActions(Lifespan.FOREVER, FixedListModel.create(myActions));
      }
    });
  }

  @Override
  public void start() {
    ReadonlyConfiguration config = WorkAreaUtil.loadEtcConfiguration(
      myWorkArea, WorkArea.ETC_WORKFLOW_XML, "cannot read workflow file");

    if (config != null) {
      try {
        registerWorkflow(config);
      } catch (ConfigurationException e) {
        Log.error(e);
      }
    }
  }

  @Override
  public void stop() {}
}
