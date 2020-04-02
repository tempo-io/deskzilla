package com.almworks.bugzilla.gui.flags.edit;

import com.almworks.bugzilla.provider.datalink.flags2.*;
import com.almworks.items.sync.SyncState;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.EnableState;
import com.almworks.util.ui.widgets.*;
import com.almworks.util.ui.widgets.util.ButtonCell;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;

class StatusCell extends ButtonCell<EditableFlag> {
  public static final StatusCell PLUS = new StatusCell(FlagStatus.PLUS, "Set flag to '+'");
  public static final StatusCell MINUS = new StatusCell(FlagStatus.MINUS, "Set flag to '\u2212'");
  public static final StatusCell REQ = new StatusCell(FlagStatus.QUESTION, "Set flag to '?'");
  public static final StatusCell CLEAR = new StatusCell(FlagStatus.UNKNOWN, "Unset flag");
  private final FlagStatus myStatus;
  private final String myTooltip;

  StatusCell(FlagStatus status, String tooltip) {
    myStatus = status;
    myTooltip = tooltip;
  }

  @Override
  protected Dimension getPrefSize(CellContext context, EditableFlag value) {
    Dimension size = super.getPrefSize(context, value);
    size.width = Math.max(size.width, size.height);
    size.height = Math.max(size.width, size.height);
    return size;
  }

  @Override
  public void paint(@NotNull GraphContext context, @Nullable EditableFlag flag) {
    if (flag == null || !isVisible(flag)) return;
    paintButton(context, myStatus == flag.getStatus() && !isClearStatus(), isEnabled(flag), flag);
  }

  protected void setupButton(AbstractButton button, boolean selected, EditableFlag flag) {
    button.setIcon(isClearStatus() ? Icons.ACTION_GENERIC_CANCEL_OR_REMOVE : null);
    button.setText(isClearStatus() ? "" : myStatus.getDisplayChar());
  }

  @Override
  protected String getTooltip(EditableFlag value) {
    return isVisible(value) && isEnabled(value) ? myTooltip : null;
  }

  protected EnableState getEnableState(EditableFlag flag) {
    if (flag == null) return EnableState.INVISIBLE;
    FlagTypeItem type = flag.getType();
    if (type == null) return EnableState.INVISIBLE;
    if (isClearStatus()) {
      if (flag.getSyncState() == SyncState.NEW) return EnableState.INVISIBLE;
      if (flag.isDeleted()) return EnableState.INVISIBLE;
      return type.allowsStatus(myStatus) ? EnableState.ENABLED : EnableState.INVISIBLE;
    }
    if (flag.isDeleted()) return myStatus == flag.getServerStatus() ? EnableState.DISABLED : EnableState.INVISIBLE;
    boolean enabled = type.allowsStatus(myStatus);
    if (myStatus == flag.getStatus()) return enabled ? EnableState.ENABLED : EnableState.DISABLED;
    return enabled ? EnableState.ENABLED : EnableState.INVISIBLE;
  }

  private boolean isClearStatus() {
    return myStatus == FlagStatus.UNKNOWN;
  }

  @Override
  protected void actionPerformed(EventContext context, EditableFlag value) {
    FlagsModelKey.setStatus(context.getHost().getWidgetData(FlagEditor.KEY_MODEL), value, myStatus);
    context.repaint();
  }
}