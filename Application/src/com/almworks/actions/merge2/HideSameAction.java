package com.almworks.actions.merge2;

import com.almworks.util.L;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;

/**
 * @author dyoma
 */
public class HideSameAction extends AnAbstractAction {
  public static final AnAction INSTANCE = new HideSameAction();

  private HideSameAction() {
    super(L.actionName("Changes Only"), Icons.ACTION_MERGE_HIDE_SAME);
    setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, L.tooltip("Show only changed fields"));
  }

  public void perform(ActionContext context) throws CantPerformException {
    MergeComponent component = context.getSourceObject(MergeComponent.ROLE);
    component.setHideSame(!component.isHideSame());
  }

  public void update(UpdateContext context) throws CantPerformException {
    super.update(context);
    context.watchModifiableRole(MergeComponent.ROLE);
    MergeComponent component = context.getSourceObject(MergeComponent.ROLE);
    context.putPresentationProperty(PresentationKey.TOGGLED_ON, component.isHideSame());
  }
}
