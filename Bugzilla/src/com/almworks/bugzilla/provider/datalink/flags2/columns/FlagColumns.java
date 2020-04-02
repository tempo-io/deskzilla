package com.almworks.bugzilla.provider.datalink.flags2.columns;

import com.almworks.api.application.*;
import com.almworks.api.application.util.DummyShadingRenderer;
import com.almworks.bugzilla.provider.datalink.flags2.*;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.util.advmodel.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.Convertor;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.CollectionRenderer;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.models.SimpleColumnAccessor;
import com.almworks.util.models.TableColumnAccessor;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.threads.Threads;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.Comparator;
import java.util.List;

import static org.almworks.util.Collections15.arrayList;

public class FlagColumns {
  public static Comparator<TableColumnAccessor<LoadedItem, ?>> COLUMNS_COMPARATOR = new Comparator<TableColumnAccessor<LoadedItem, ?>>() {
    @Override
    public int compare(TableColumnAccessor<LoadedItem, ?> o1, TableColumnAccessor<LoadedItem, ?> o2) {
      boolean a1 = isAggregating(o1);
      boolean a2 = isAggregating(o2);
      if (a1 && !a2) return -1;
      if (a2 && !a1) return 1;
      return TableColumnAccessor.NAME_ORDER.compare(o1, o2);
    }

    private boolean isAggregating(TableColumnAccessor<LoadedItem, ?> column) {
      return column.getId().startsWith(AGGREGATING_COL_ID_PRE);
    }
  };
  private static final String COL_ID_PRE = "_bz_flag_";
  private static final String AGGREGATING_COL_ID_PRE = COL_ID_PRE + "_almworks_aggregating_";
  private final UIFlagData myData;
  private AListModel<TableColumnAccessor<LoadedItem,?>> myAggregateColumns;

  public FlagColumns(UIFlagData data) {
    myData = data;
  }

  public AListModel<? extends TableColumnAccessor<LoadedItem, ?>> getColumnsForConnection(Lifespan life, long connection) {
    AListModel<TableColumnAccessor<LoadedItem, ?>> aggregateColumns = getAggregateColumns();
    AListModel<TableColumnAccessor<LoadedItem, ?>> perFlagColumns = createPerFlagColumns(life, connection, myData.getTypesModel());
    return createCombinedModel(life, aggregateColumns, perFlagColumns);
  }

  private static AListModel<TableColumnAccessor<LoadedItem, ?>> createPerFlagColumns(Lifespan life, final long connection,
    AListModel<FlagTypeItem> allTypesModel) {
    ResolvedItem.SelectConnection filter = new ResolvedItem.SelectConnection(connection);
    AListModel<TableColumnAccessor<LoadedItem, ?>> columns = FilteringConvertingListDecorator.create(life,
      allTypesModel, filter, CREATE_COLUMN);
    columns = SortedListDecorator.create(life, columns, TableColumnAccessor.NAME_ORDER);
    return columns;
  }

  private static AListModel<TableColumnAccessor<LoadedItem, ?>> createCombinedModel(
    Lifespan life,
    final AListModel<TableColumnAccessor<LoadedItem, ?>> aggregateColumns,
    final AListModel<TableColumnAccessor<LoadedItem, ?>> perFlagColumns)
  {
    final OrderListModel<TableColumnAccessor<LoadedItem, ?>> dependent = OrderListModel.create();

    final ChangeListener listener = new ChangeListener() {
      @Override
      public void onChange() {
        if(perFlagColumns.getSize() == 0) {
          dependent.clear();
        } else if(dependent.getSize() == 0) {
          dependent.addAll(aggregateColumns.toList());
        }
      }
    };
    listener.onChange();
    perFlagColumns.addChangeListener(life, ThreadGate.AWT_QUEUED, listener);

    return SegmentedListModel.create(dependent, perFlagColumns);
  }

  private AListModel<TableColumnAccessor<LoadedItem, ?>> getAggregateColumns() {
    Threads.assertAWTThread();
    if (myAggregateColumns == null) {
      myAggregateColumns = createAggregateColumns();
    }
    return myAggregateColumns;
  }

  private static AListModel<TableColumnAccessor<LoadedItem, ?>> createAggregateColumns() {
    List<TableColumnAccessor<LoadedItem, ?>> columns = arrayList();
    columns.add(createAggregateColumn("all", "All Flags", AggregateComparator.create(null), AggregateRenderer.ALL));
    columns.add(createAggregateColumn("plus", "Flags+", AggregateComparator.create('+'), AggregateRenderer.PLUS));
    columns.add(createAggregateColumn("minus", "Flags" + FlagStatus.MINUS.getDisplayPresentation(),
      AggregateComparator.create('-'), AggregateRenderer.MINUS));
    columns.add(createAggregateColumn("request", "Flags?", AggregateComparator.create('?'), AggregateRenderer.QUESTION));
    return FixedListModel.create(columns);
  }

  private static SimpleColumnAccessor<LoadedItem> createAggregateColumn(String type, String name, Comparator<LoadedItem> comparator, CanvasRenderer<PropertyMap> renderer) {
    return new SimpleColumnAccessor<LoadedItem>(AGGREGATING_COL_ID_PRE + type, name, columnRenderer(renderer, BugzillaKeys.dummy), comparator);
  }

  private static CollectionRenderer<LoadedItem> columnRenderer(CanvasRenderer<PropertyMap> canvasRenderer, @Nullable ModelKey<Boolean> dummyFlag) {
    return Renderers.convertingRenderer(new DummyShadingRenderer(Renderers.createRenderer(canvasRenderer), dummyFlag), LoadedItem.GET_VALUES);
  }

  private static String getStringId(FlagTypeItem type) {
    return COL_ID_PRE + "bug_" + (type.getTypeId() >= 0 ? type.getTypeId() : type.getDisplayName());
  }

  private static final Convertor<FlagTypeItem, TableColumnAccessor<LoadedItem, ?>> CREATE_COLUMN =
    new Convertor<FlagTypeItem, TableColumnAccessor<LoadedItem, ?>>() {
      @Override
      public TableColumnAccessor<LoadedItem, ?> convert(FlagTypeItem type) {
        return createColumn(type);
      }

      private TableColumnAccessor<LoadedItem, ?> createColumn(FlagTypeItem type) {
        CollectionRenderer<LoadedItem> renderer = columnRenderer(new FlagsRenderer(type), BugzillaKeys.dummy);
        Comparator<LoadedItem> comparator = OneTypeComparator.create(type);
        return new SimpleColumnAccessor<LoadedItem>(getStringId(type), type.getDisplayName(), renderer, comparator);
      }
    };
}
