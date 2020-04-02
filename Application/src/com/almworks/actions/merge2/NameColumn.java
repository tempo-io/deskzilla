package com.almworks.actions.merge2;

import com.almworks.util.L;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.images.Icons;
import com.almworks.util.models.BaseTableColumnAccessor;
import com.almworks.util.ui.EmptyIcon;
import com.almworks.util.ui.RowIcon;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * @author : Dyoma
 */
class NameColumn extends BaseTableColumnAccessor<KeyMergeState, String> {
  private static final MyRenderer NAME_RENDERER = new MyRenderer();

  protected NameColumn() {
    super(L.tableColumn("Attribute"), Renderers.createRenderer(NAME_RENDERER), String.CASE_INSENSITIVE_ORDER);
  }

  public String getValue(KeyMergeState key) {
    return key.getDisplayableName();
  }

  private static class MyRenderer implements CanvasRenderer<KeyMergeState> {
    private static final Icon EMPTY = EmptyIcon.sameSize(Icons.MERGE_STATE_CONFLICT);
    private static final Icon NOT_MARKED = EmptyIcon.sameSize(Icons.POINTING_TRIANGLE);

    public void renderStateOn(CellState state, Canvas canvas, KeyMergeState item) {
      Border border = state.getBorder();
      if (border != null) {
        canvas.setForeground(state.getForeground());
        canvas.appendText(item.getDisplayableName());
        Icon icon;
        if (!item.isResolved()) {
          canvas.setFontStyle(Font.BOLD);
          if (item.isConflict()) {
            if(!state.isSelected()) {
              canvas.setForeground(Color.RED);
            }
            icon = Icons.MERGE_STATE_CONFLICT;
          } else if (item.isLocalChanged())
            icon = Icons.MERGE_STATE_CHANGED_LOCALLY;
          else if (item.isRemoteChanged())
            icon = Icons.MERGE_STATE_CHANGED_REMOTELY;
          else {
            assert false : item;
            icon = EMPTY;
          }
        } else
          icon = EMPTY;
        canvas.setIcon(RowIcon.create(state.isSelected() ? Icons.POINTING_TRIANGLE : NOT_MARKED, icon));
      }
    }
  }
}
