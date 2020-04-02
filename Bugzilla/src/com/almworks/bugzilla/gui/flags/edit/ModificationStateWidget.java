package com.almworks.bugzilla.gui.flags.edit;

import com.almworks.bugzilla.provider.datalink.flags2.EditableFlag;
import com.almworks.bugzilla.provider.datalink.flags2.FlagVersion;
import com.almworks.items.sync.SyncState;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.swing.AwtUtil;
import com.almworks.util.ui.widgets.*;
import com.almworks.util.ui.widgets.util.LeafRectCell;
import org.almworks.util.Log;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;

class ModificationStateWidget extends LeafRectCell<EditableFlag> {
  static final Widget<EditableFlag> INSTANCE = new ModificationStateWidget();
  private static Icon[] ICONS = new Icon[]{
    Icons.ARTIFACT_STATE_HAS_UNSYNC_CHANGES,
    Icons.ARTIFACT_STATE_LOCALLY_ADDED,
    Icons.ARTIFACT_STATE_LOCALLY_REMOVED,
    Icons.ARTIFACT_STATE_HAS_SYNC_CONFLICT,
    Icons.MERGE_STATE_CHANGED_LOCALLY
  };

  @Override
  protected Dimension getPrefSize(CellContext context, EditableFlag value) {
    Dimension result = new Dimension();
    for (Icon icon : ICONS) AwtUtil.maxDimensions(result, icon.getIconWidth(), icon.getIconHeight());
    return result;
  }

  private static Icon getSyncIcon(SyncState state) {
    switch (state) {
    case SYNC: return null;
    case NEW: return Icons.ARTIFACT_STATE_LOCALLY_ADDED;
    case EDITED: return Icons.ARTIFACT_STATE_HAS_UNSYNC_CHANGES;
    case LOCAL_DELETE: return Icons.ARTIFACT_STATE_LOCALLY_REMOVED;
    case CONFLICT:
    case DELETE_MODIFIED: return Icons.ARTIFACT_STATE_HAS_SYNC_CONFLICT;
    case MODIFIED_CORPSE: return Icons.MERGE_STATE_CHANGED_LOCALLY;
    default: Log.error("Unknown state " + state); return null;
    }
  }

  @Override
  public void paint(@NotNull GraphContext context, @Nullable EditableFlag value) {
    Icon icon = value != null ? getSyncIcon(value.getSyncState()) : null;
    if (icon == null) return;
    JComponent component = context.getHost().getHostComponent();
    int x = (context.getWidth() - icon.getIconWidth()) / 2;
    int y = (context.getHeight() - icon.getIconHeight()) / 2;
    icon.paintIcon(component, context.getGraphics(), x, y);
  }

  @Override
  public void processEvent(@NotNull EventContext context, @Nullable EditableFlag value, TypedKey<?> reason) {
    if (reason == TooltipRequest.KEY) {
      if (value != null && value.getSyncState() != SyncState.SYNC) //noinspection ConstantConditions
        context.getData(TooltipRequest.KEY).setTooltip(getTooltip(value));
      context.consume();
    }
    super.processEvent(context, value, reason);
  }

  private String getTooltip(EditableFlag value) {
    SyncState state = value.getSyncState();
    StringBuilder builder = new StringBuilder();
    switch (state) {
    case NEW: return "Flag has been set, but not uploaded yet";
    case MODIFIED_CORPSE: return "Flag has been unset on the server";
    case EDITED: builder.append("Flag has been changed"); break;
    case LOCAL_DELETE: builder.append("Flag has been unset"); break;
    case DELETE_MODIFIED:
    case CONFLICT: builder.append("Flag has conflicting changes"); break;
    case SYNC:
    default:
      Log.error("Unknown edit flag state " + state);
      return null;
    }
    FlagVersion original = value.getOriginalVersion();
    if (original == null) Log.error("No server version for modified flag " + value);
    else {
      builder.append(", server version is ");
      builder.append(FlagVersion.TO_BUGZILLA_PRESENTATION_HYPHEN.convert(original));
    }
    return builder.toString();
  }
}
