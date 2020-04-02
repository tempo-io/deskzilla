package com.almworks.api.actions;

import com.almworks.api.gui.DefaultCloseConfirmation;
import com.almworks.util.L;
import com.almworks.util.ui.actions.ActionContext;
import com.almworks.util.ui.actions.CantPerformException;

public abstract class EditorCloseConfirmation extends DefaultCloseConfirmation {
  protected EditorCloseConfirmation() {
    super(true);
  }

  @Override
  protected String getQuestion() {
    return L.content("Would you like to save entered information as a draft?");
  }

  @Override
  protected void answeredNo(ActionContext context) throws CantPerformException {
  }
}
