package com.almworks.api.gui;

import com.almworks.util.L;
import com.almworks.util.ui.DialogsUtil;
import com.almworks.util.ui.actions.*;

import static com.almworks.util.ui.DialogsUtil.*;

public abstract class DefaultCloseConfirmation implements AnActionListener {
  private final boolean myShowCancel;

  protected DefaultCloseConfirmation(boolean showCancel) {
    myShowCancel = showCancel;
  }

  public void perform(ActionContext context) throws CantPerformException {
    boolean needsConfirmation = isCloseConfirmationRequired(context);
    if (!needsConfirmation)
      return;
    context.getSourceObject(WindowController.ROLE).toFront();
    int reply = DialogsUtil.askUser(context.getComponent(), getQuestion(), L.dialog("Confirm Close Window"), myShowCancel ? YES_NO_CANCEL_OPTION : YES_NO_OPTION);
    switch(reply) {
    case CLOSED_OPTION:
    case CANCEL_OPTION:
      cancelled(context);
      break;
    case DialogsUtil.NO_OPTION:
      answeredNo(context);
      break;
    case DialogsUtil.YES_OPTION:
      answeredYes(context);
      break;
    default:
      assert false : reply;
    }
  }

  protected void answeredNo(ActionContext context) throws CantPerformException {
    throw new CantPerformExceptionSilently("Not confirmed");
  }

  protected void answeredYes(ActionContext context) throws CantPerformException {
  }

  protected void cancelled(ActionContext context) throws CantPerformException {
    throw new CantPerformExceptionSilently("Cancelled");
  }

  protected String getQuestion() {
    return L.content("Are you sure you want to close this window?");
  }

  protected abstract boolean isCloseConfirmationRequired(ActionContext context) throws CantPerformException;
}
