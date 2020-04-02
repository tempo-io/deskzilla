package com.almworks.bugzilla.gui.flags;

import com.almworks.api.application.ItemKey;
import com.almworks.bugzilla.provider.datalink.flags2.Flag;
import com.almworks.bugzilla.provider.datalink.flags2.FlagVersion;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.FixedListModel;
import com.almworks.util.collections.Containers;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.models.BaseTableColumnAccessor;
import com.almworks.util.models.TableColumnAccessor;

import java.awt.*;
import java.util.Comparator;

import static com.almworks.util.components.renderer.Renderers.textCanvasRenderer;

public class BugFlagsColumns {
  private final static CanvasRenderer<ItemKey> USER_RENDERER = new CanvasRenderer<ItemKey>() {
    @Override
    public void renderStateOn(CellState state, Canvas canvas, ItemKey item) {
      if (item != null) {
        canvas.appendText(item.getDisplayName());
      }
    }
  };
  private final static Comparator<ItemKey> USER_COMPARATOR = Containers.convertingComparator(ItemKey.GET_ID.toReadAccessor(), Containers.stringComparator());

  private static BaseTableColumnAccessor<FlagVersion, FlagVersion> nameStatus() {
    final ColumnTooltipProvider<FlagVersion> tooltipProvider = new ColumnTooltipProvider<FlagVersion>() {
      @Override
      public String getTooltip(CellState cellState, FlagVersion row, Point cellPoint, Rectangle cellRect) {
        return row.getDescription();
      }
    };
    CanvasRenderer<Flag> renderer = textCanvasRenderer(FlagVersion.TO_COMPRESSED_PRESENTATION);
    return new BaseTableColumnAccessor<FlagVersion, FlagVersion>("Flag", renderer, Flag.ORDER) {
      @Override
      public FlagVersion getValue(FlagVersion row) {
        return row;
      }

      @Override
       public ColumnTooltipProvider<FlagVersion> getTooltipProvider() {
        return tooltipProvider;
      }
    };
  }

  private static BaseTableColumnAccessor<FlagVersion, ItemKey> setter() {
    return new BaseTableColumnAccessor<FlagVersion, ItemKey>("Setter", USER_RENDERER, USER_COMPARATOR) {
      @Override
      public ItemKey getValue(FlagVersion row) {
        return row.getSetterKey();
      }
    };
  }

  private static BaseTableColumnAccessor<FlagVersion, ItemKey> requestee() {
    return new BaseTableColumnAccessor<FlagVersion, ItemKey>("Requestee", USER_RENDERER, USER_COMPARATOR) {
      @Override
      public ItemKey getValue(FlagVersion row) {
        return row.getRequesteeKey();
      }
    };
  }

  public static AListModel<? extends TableColumnAccessor<FlagVersion, ?>> all() {
    return FixedListModel.create(setter(), nameStatus(), requestee());
  }
}
