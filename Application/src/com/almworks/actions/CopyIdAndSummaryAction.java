package com.almworks.actions;

import com.almworks.api.actions.ItemActionUtils;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.engine.Connection;
import com.almworks.integers.LongList;
import com.almworks.integers.LongListIterator;
import com.almworks.items.api.*;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.Terms;
import com.almworks.util.commons.Procedure;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.i18n.Local;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

public class CopyIdAndSummaryAction extends SimpleAction {
  public CopyIdAndSummaryAction() {
    super("C&opy ID && Summary");
    setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, "Copy ID and Summary of selected " + Terms.ref_artifacts + " to clipboard");
    setDefaultPresentation(PresentationKey.ENABLE, EnableState.DISABLED);
    setDefaultPresentation(PresentationKey.SHORTCUT,
      KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
    watchRole(ItemWrapper.ITEM_WRAPPER);
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    final Map<Connection, LongList> map = ItemActionUtils.getItemsGroupedByConnection(context);
    Database.require().readForeground(new ReadTransaction<StringBuilder>() {
      public StringBuilder transaction(DBReader reader) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<Connection, LongList> e : map.entrySet()) {
          Connection connection = e.getKey();
          for (LongListIterator i = e.getValue().iterator(); i.hasNext();) {
            String s = connection.getExternalIdSummaryString(SyncUtils.readTrunk(reader, i.next()));
            if (s != null && s.length() > 0) {
              result.append(s).append('\n');
            }
          }
        }
        return result;
      }
    }).onSuccess(ThreadGate.AWT, new Procedure<StringBuilder>() {
      public void invoke(StringBuilder arg) {
        if (arg.length() == 0) return;
        UIUtil.copyToClipboard(arg.toString());
        Toolkit.getDefaultToolkit().beep();
      }
    });
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    int count = 0;
    try {
      List<ItemWrapper> wrappers = context.getSourceCollection(ItemWrapper.ITEM_WRAPPER);
      for (ItemWrapper wrapper : wrappers) {
        if (wrapper.services().isDeleted())
          return;
        Connection connection = wrapper.getConnection();
        if (connection == null || connection.getState().getValue().isDegrading())
          return;
        count++;
      }
      context.setEnabled(count > 0);
    } finally {
      if (count != 1) {
        context.putPresentationProperty(PresentationKey.SHORT_DESCRIPTION,
          Local.parse("Copy to clipboard IDs and summaries of the selected " + Terms
            .ref_artifacts));
      } else {
        context.putPresentationProperty(PresentationKey.SHORT_DESCRIPTION,
          Local.parse("Copy to clipboard ID and summary of the selected " + Terms
            .ref_artifact));
      }
    }
  }
}
