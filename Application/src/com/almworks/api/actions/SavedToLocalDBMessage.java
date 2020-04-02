package com.almworks.api.actions;

import com.almworks.api.gui.DialogManager;
import com.almworks.api.gui.ShowOnceMessageBuilder;
import com.almworks.items.sync.EditCommit;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.i18n.Local;
import com.almworks.util.ui.actions.ActionContext;
import com.almworks.util.ui.actions.CantPerformException;

import javax.swing.*;

public class SavedToLocalDBMessage extends EditCommit.Adapter {
  private final DialogManager myManager;
  private final String myMessageId;

  public SavedToLocalDBMessage(DialogManager manager, String messageId) {
    myManager = manager;
    myMessageId = messageId;
  }

  public static void addTo(ActionContext context, AggregatingEditCommit commit, String messageId) throws CantPerformException {
    DialogManager manger = context.getSourceObject(DialogManager.ROLE);
    commit.addProcedure(ThreadGate.AWT, new SavedToLocalDBMessage(manger, messageId));
  }

  @Override
  public void onCommitFinished(boolean success) {
    if (!success) return;
    ShowOnceMessageBuilder builder = myManager.createOnceMessageBuilder(myMessageId);
    builder.setTitle(L.frame(Local.parse("Change " + Terms.ref_Artifact + " - " + Terms.ref_Deskzilla)));
    builder.setMessage(L.content("Your changes are saved in the local database"),
      JOptionPane.INFORMATION_MESSAGE);
    builder.showMessage();
  }
}
