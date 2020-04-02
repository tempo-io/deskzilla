package com.almworks.sysprop;

import com.almworks.util.ui.actions.*;

public class SystemPropertiesAction extends SimpleAction {
  public SystemPropertiesAction() {
    super("System Properties\u2026");
    updateOnChange(SystemPropertiesDialog.SHOWING);
  }

  @Override
  protected void customUpdate(UpdateContext context) throws CantPerformException {
    context.setEnabled(!SystemPropertiesDialog.SHOWING.getValue());
  }

  @Override
  protected void doPerform(ActionContext context) throws CantPerformException {
    SystemPropertiesDialog.showDialog(context);
  }
}
