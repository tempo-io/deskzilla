package com.almworks.explorer.workflow;

import com.almworks.api.actions.*;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.MetaInfo;
import com.almworks.api.dynaforms.EditPrimitive;
import com.almworks.api.explorer.WorkflowComponent;
import com.almworks.api.explorer.rules.RulesManager;
import com.almworks.integers.LongList;
import com.almworks.util.config.Configuration;
import com.almworks.util.ui.actions.*;
import org.jetbrains.annotations.*;

import java.util.List;

/**
 * @author dyoma
 */
class WorkflowTransitionAction extends BaseEditAction.EditInWindowAction implements IdentifiableAction {
  private final WorkflowTransition myTransition;
  private final MyEditor myEditor;

  public WorkflowTransitionAction(WorkflowTransition transition) {
    super(NON_ZERO);
    myEditor = new MyEditor(transition);
    myTransition = transition;
    setDefaultPresentation(PresentationKey.NAME, transition.getName().getTextWithMnemonic());
    setDefaultPresentation(PresentationKey.SMALL_ICON, transition.getIcon());
  }

  @Override
  @NotNull
  public EditorWindowCreator getWindowCreator() {
    return myEditor;
  }

  protected void updateEnabledAction(UpdateContext context, List<ItemWrapper> items) throws CantPerformException {
    context.getSourceObject(WorkflowComponent.ROLE);
    final RulesManager rulesManager = context.getSourceObject(RulesManager.ROLE);
    final MetaInfo metaInfo = ItemActionUtils.getUniqueMetaInfo(items);
    context.setEnabled(myTransition.isApplicable(rulesManager, metaInfo, items));
  }

  public Object getIdentity() {
    return myTransition.getIdentity();
  }

  static class MyEditor extends ModularEditorWindow {
    private final WorkflowTransition myTransition;

    private MyEditor(WorkflowTransition transition) {
      myTransition = transition;
    }

    @Override
    protected List<? extends EditPrimitive> getActionFields(List<ItemWrapper> items, Configuration configuration) {
      return myTransition.getEditScript();
    }

    @Override
    protected String getActionTitle(List<ItemWrapper> items) {
      return myTransition.getName().getText();
    }

    @Override
    protected String getFrameId() {
      return "WorkFlowAction." + myTransition.getWindowId();
    }

    @NotNull
    @Override
    protected LongList addToLock(List<ItemWrapper> primaryItems, ActionContext context) throws CantPerformException {
      // todo :refactoring: check if has anything to lock
      return super.addToLock(primaryItems, context);
    }
  }
}
