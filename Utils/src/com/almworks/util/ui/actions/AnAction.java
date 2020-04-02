package com.almworks.util.ui.actions;

import org.almworks.util.Collections15;

import java.util.List;


/**
 * @author : Dyoma
 */
public interface AnAction extends AnActionListener {
  List<AnAction> EMPTY_LIST = Collections15.emptyList(); // todo inline?
  AnAction[] EMPTY_ARRAY = new AnAction[0];

  AnAction DEAF = new ConstEnabledAction(EnableState.ENABLED);
  AnAction DISABLED = new ConstEnabledAction(EnableState.DISABLED);
  AnAction INVISIBLE = new ConstEnabledAction(EnableState.INVISIBLE);

  void update(UpdateContext context) throws CantPerformException;

  public static class ConstEnabledAction implements AnAction {
    private final EnableState myEnable;

    public ConstEnabledAction(EnableState enable) {
      myEnable = enable;
    }

    public void update(UpdateContext context) throws CantPerformException {
      context.setEnabled(myEnable);
    }

    public void perform(ActionContext context) throws CantPerformException {
    }
  }
}
