package com.almworks.timetrack.gui.timesheet;

import com.almworks.api.application.*;
import com.almworks.api.gui.DialogManager;
import com.almworks.api.gui.DialogResult;
import com.almworks.timetrack.api.TimeTrackingCustomizer;
import com.almworks.timetrack.impl.TaskTiming;
import com.almworks.timetrack.impl.TimeTrackingUtil;
import com.almworks.util.*;
import com.almworks.util.advmodel.FixedListModel;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.collections.*;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.*;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.config.Configuration;
import com.almworks.util.datetime.DateUtil;
import com.almworks.util.exec.Context;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.model.ModelUtils;
import com.almworks.util.model.ValueModel;
import com.almworks.util.models.*;
import com.almworks.util.properties.Role;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.*;
import com.almworks.util.ui.swing.AwtUtil;
import com.almworks.util.ui.swing.BaseRendererComponent;
import org.almworks.util.*;
import org.almworks.util.detach.DetachComposite;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

import static javax.swing.SwingConstants.CENTER;

public class TimesheetForm implements UIComponentWrapper {
  private static final SimpleDateFormat DAY_FORMAT = new SimpleDateFormat(" dd ", Locale.US);
  private static final SimpleDateFormat MONTH_FORMAT = new SimpleDateFormat("MMMM yyyy", Locale.US);
  private static final long H2 = 2 * Const.HOUR;
  private static final Role<Pair<TaskEntry, DateEntry>> CELL = Role.anonymous();

  private AGrid<TaskEntry, DateEntry, Long> myGrid;

  private final DetachComposite myLife = new DetachComposite();
  private final TimesheetFormData myData;

  private final OrderListModel<LoadedItem> myTasksSourceModel = OrderListModel.create();
  private final OrderListModel<TaskEntry> myTasks = OrderListModel.create();
  private final ValueModel<List<GroupingFunction>> myGroupings = ValueModel.create();
  private final Comparator<TaskEntry> myTaskEntryComparator = new TaskEntryComparator();
  private final Color[] myColorGrades = new Color[7];

  private final Map<TaskEntry, Map<DateEntry, Long>> myCache = Collections15.hashMap();
  private final Map<Integer, Icon> myIndentIconCache = Collections15.hashMap();
  private int myRowDepth;
  private List<DateEntry> myDateEntries;
  private Color myWeekendColor;
  private ConfiguredSplitPane mySplitPane;

  private final ASortedTable<WorkPeriod> myDetailsTable;
  private final OrderListModel<WorkPeriod> myDetailsModel = OrderListModel.create();
  private final JLabel myDetailsLabel = new JLabel();
  private static final ItemKeyStub TOTAL_KEY = new ItemKeyStub("total", "Total", ItemOrder.NO_ORDER);
  static final Color EXCLUDED_COLOR =
    ColorUtil.between(AwtUtil.getTextComponentForeground(), UIUtil.getEditorBackground(), 0.6F);

  private final EditWorkPeriodAction myEditWorkPeriodAction = new EditWorkPeriodAction();

  private final class Includer extends ButtonActor.Checkbox<WorkPeriod> {
    protected boolean isSelected(WorkPeriod item) {
      return !item.isExcluded();
    }

    protected void act(WorkPeriod edited) {
      togglePulish(edited);
      myData.onWorkPeriodChanged();
      refreshData();
    }
  };

  private final TimeTrackingCustomizer myCustomizer = Context.require(TimeTrackingCustomizer.ROLE);

  public TimesheetForm(TimesheetFormData data, Configuration config) {
    myData = data;
    myGrid = createGrid();
    myDetailsTable = createTable();
    myDetailsTable.setCollectionModel(myDetailsModel);
    myDetailsTable.setDataRoles(WorkPeriod.ROLE);
    myDetailsTable.getTable().addDoubleClickListener(myLife, new CollectionCommandListener<WorkPeriod>() {
      public void onCollectionCommand(ACollectionComponent<WorkPeriod> workPeriodACollectionComponent, int index,
        WorkPeriod element)
      {
        ActionUtil.performSafe(myEditWorkPeriodAction, myDetailsTable);
      }
    });

    final JPanel detailsPanel = new JPanel(new BorderLayout());

    final ToolbarBuilder tb = ToolbarBuilder.smallVisibleButtons();
    tb.addAction(new AddWorkPeriodAction(), myDetailsTable);
    tb.addAction(myEditWorkPeriodAction, myDetailsTable);
    tb.addAction(new DeleteWorkPeriodAction(), myDetailsTable);

    final JPanel tableTopPanel = new JPanel(new BorderLayout());
    tableTopPanel.add(tb.createHorizontalToolbar(), BorderLayout.WEST);
    myDetailsLabel.setBorder(new EmptyBorder(0, 5, 0, 0));
    tableTopPanel.add(myDetailsLabel, BorderLayout.CENTER);

    detailsPanel.add(tableTopPanel, BorderLayout.NORTH);
    detailsPanel.add(myDetailsTable);

    mySplitPane = ConfiguredSplitPane.createTopBottom(
      myGrid.getComponent(), detailsPanel, config.getOrCreateSubset("splitpane"), 0.7F);

    myWeekendColor = ColorUtil.between(UIUtil.getEditorBackground(), Color.GRAY, 0.1F);

    if(Aqua.isAqua()) {
      Aqua.makeLeopardStyleSplitPane(mySplitPane);
      myGrid.getComponent().setBorder(Aqua.MAC_BORDER_NORTH);
      tableTopPanel.setBorder(Aqua.MAC_BORDER_SOUTH);
      myDetailsTable.setBorder(Aqua.MAC_BORDER_SOUTH);
      ScrollBarPolicy.setDefaultWithHorizontal(myDetailsTable, ScrollBarPolicy.AS_NEEDED);
    }
  }

  private boolean showEditWorkForm(ActionContext context, EditWorkPeriodForm form, String title)
    throws CantPerformException
  {
    DialogManager dialogManager = Context.require(DialogManager.class);
    DialogResult<Boolean> result = DialogResult.create(context, "editWorkPeriod");
    result.setOkResult(true);
    result.setCancelResult(false);
    Boolean r = result.showModal(title, form.getComponent());
    return r != null && r;
  }

  private int findPreceding(List<WorkPeriod> work, long time) {
    if (work == null || work.isEmpty())
      return -1;
    int bestIndex = -1;
    long bestDiff = Long.MAX_VALUE;
    for (int i = 0; i < work.size(); i++) {
      TaskTiming t = work.get(i).getTiming();
      if (t.getStarted() >= time) {
        break;
      }
      long diff = time - t.getStopped();
      if (diff >= 0 && diff < bestDiff) {
        bestDiff = diff;
        bestIndex = i;
      }
    }
    return bestIndex;
  }

  private long suggestTime(ActionContext context) {
    List<WorkPeriod> c = null;
    try {
      c = context.getSourceCollection(WorkPeriod.ROLE);
    } catch (CantPerformException e) {
      // ignore
    }
    if (c != null && !c.isEmpty()) {
      WorkPeriod last = c.get(c.size() - 1);
      return last.getTiming().getStopped();
    }
    AGridSelectionModel sm = myGrid.getSelectionModel();
    if (sm.getSelectionType() != AGridSelectionModel.NOTHING_SELECTED) {
      int col = sm.getColumnTo();
      if (col >= 0 && col < myDateEntries.size()) {
        DateEntry dateEntry = myDateEntries.get(col);
        return dateEntry.getEnd();
      }
    }
    return System.currentTimeMillis();
  }

  private ASortedTable<WorkPeriod> createTable() {
    final ASortedTable<WorkPeriod> table = new ASortedTable<WorkPeriod>();
    table.setGridHidden();
    table.setStriped(true);

    final List<TableColumnAccessor<WorkPeriod, ?>> columns = Collections15.arrayList();

    columns.add(
      TableColumnBuilder.<WorkPeriod, WorkPeriod>create("include", "Publish")
        .setEditor(new Includer())
        .setRenderer(new Includer())
        .setConvertor(Convertor.<WorkPeriod>identity())
        .setSizePolicy(ColumnSizePolicy.Calculated.freeLetterMWidth(5))
        .createColumn());

    columns.add(new SimpleColumnAccessor<WorkPeriod>(Local.parse(Terms.ref_Artifact_ID),
      new Renderers.DefaultCollectionRenderer<WorkPeriod>(new CanvasRenderer<WorkPeriod>() {
        public void renderStateOn(CellState state, Canvas canvas, WorkPeriod item) {
          prerender(state, canvas, item);
          canvas.appendText(myCustomizer.getItemKey(item.getArtifact()));
        }
      }), new Comparator<WorkPeriod>() {
        final Comparator<ItemWrapper> keyComparator = myCustomizer.getArtifactByKeyComparator();
        public int compare(WorkPeriod o1, WorkPeriod o2) {
          return keyComparator.compare(o1.getArtifact(), o2.getArtifact());
        }
      }));

    columns.add(new SimpleColumnAccessor<WorkPeriod>("Started",
      new Renderers.DefaultCollectionRenderer<WorkPeriod>(new CanvasRenderer<WorkPeriod>() {
        public void renderStateOn(CellState state, Canvas canvas, WorkPeriod item) {
          prerender(state, canvas, item);
          if (item != null)
            canvas.appendText(DateUtil.toLocalDateTime(new Date(item.getTiming().getStarted())));
        }
      }), new Comparator<WorkPeriod>() {
        public int compare(WorkPeriod o1, WorkPeriod o2) {
          return Containers.compareLongs(o1.getTiming().getStarted(), o2.getTiming().getStarted());
        }
      }));

    columns.add(new SimpleColumnAccessor<WorkPeriod>("Finished",
      new Renderers.DefaultCollectionRenderer<WorkPeriod>(new CanvasRenderer<WorkPeriod>() {
        public void renderStateOn(CellState state, Canvas canvas, WorkPeriod item) {
          prerender(state, canvas, item);
          if (item != null)
            canvas.appendText(DateUtil.toLocalDateTime(new Date(item.getTiming().getStopped())));
        }
      }), new Comparator<WorkPeriod>() {
        public int compare(WorkPeriod o1, WorkPeriod o2) {
          return Containers.compareLongs(o1.getTiming().getStarted(), o2.getTiming().getStopped());
        }
      }));

    columns.add(new SimpleColumnAccessor<WorkPeriod>("Hours Worked",
      new Renderers.DefaultCollectionRenderer<WorkPeriod>(new CanvasRenderer<WorkPeriod>() {
        public void renderStateOn(CellState state, Canvas canvas, WorkPeriod item) {
          prerender(state, canvas, item);
          if (item == null)
            return;
          int seconds = getDurationSeconds(item);
          canvas.appendText(DateUtil.getHoursDurationFixed(seconds));
        }
      }), new Comparator<WorkPeriod>() {
        public int compare(WorkPeriod o1, WorkPeriod o2) {
          int s1 = getDurationSeconds(o1);
          int s2 = getDurationSeconds(o2);
          return Containers.compareInts(s1, s2);
        }
      }));

    columns.add(new SimpleColumnAccessor<WorkPeriod>("Comment",
      new Renderers.DefaultCollectionRenderer<WorkPeriod>(new CanvasRenderer<WorkPeriod>() {
        public void renderStateOn(CellState state, Canvas canvas, WorkPeriod item) {
          prerender(state, canvas, item);
          String c = item.getTiming().getComments();
          if (c != null)
            canvas.appendText(c);
        }
      })));

    table.setColumnModel(FixedListModel.create(columns));

    final JComponent c = table.getSwingComponent();
    c.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle");
    c.getActionMap().put("toggle", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        final List<WorkPeriod> items = myDetailsTable.getSelectionAccessor().getSelectedItems();
        for (final WorkPeriod item : items) {
          togglePulish(item);
        }
        myData.onWorkPeriodChanged();
        refreshData();
      }
    });

    return table;
  }

  private void togglePulish(WorkPeriod item) {
    item.setExcluded(!item.isExcluded());
    myData.setSpentDeltaNoUpdate(item.getArtifact(), null);
  }

  private static int getDurationSeconds(WorkPeriod item) {
    return item.getTiming().getLength();
  }

  private void prerender(CellState state, Canvas canvas, WorkPeriod item) {
    if (/*!state.isSelected() && */item.isExcluded()) {
      canvas.setForeground(EXCLUDED_COLOR);
    }
  }

  private static AGrid<TaskEntry, DateEntry, Long> createGrid() {
    AGrid<TaskEntry, DateEntry, Long> grid = AGrid.create();
    grid.setRowHeaderMaxColumns(30);
    JPanel panel = grid.createCornerPanel();
    ALabel corn = new ALabel("Publish Hours Worked"); // todo:
    UIUtil.adjustFont(corn, 0.9F, Font.ITALIC, true);
    panel.add(corn);
    grid.setTopLeftCorner(panel);
    return grid;
  }

  private static TimesheetFormData adjustForTesting(TimesheetFormData data) {
    Map<LoadedItem, List<TaskTiming>> map = Collections15.hashMap();
    Random random = new Random();
    for (Map.Entry<LoadedItem, List<TaskTiming>> e : data.getTimeMapForPublish().entrySet()) {
      List<TaskTiming> list = Collections15.arrayList();
      list.addAll(e.getValue());
      long b = e.getValue().isEmpty() ? System.currentTimeMillis() : e.getValue().get(0).getStarted();

      for (int i = 0; i < 20; i++) {
        b -= random.nextInt(100) * Const.HOUR;
        long stop = b;
        b -= (10 + random.nextInt(60 * 24) * Const.MINUTE);
        long start = b;
        list.add(0, new TaskTiming(start, stop, ""));
      }

      map.put(e.getKey(), list);
    }
    return new TimesheetFormData(map, null, null);
  }

  public void init() {
    setupGroupings();
    setupCells();
    setupRows();
    setupColumns();

    AGridSelectionModel gridSelection = myGrid.getSelectionModel();
    gridSelection.getModifiable().addAWTChangeListener(new ChangeListener() {
      public void onChange() {
        updateTableFromSelection();
      }
    });
    updateTableFromSelection();
  }

  private void updateTableFromSelection() {
    AGridSelectionModel sm = myGrid.getSelectionModel();
    List<WorkPeriod> data = myData.getWorkList();
    int total = data.size();
    int excluded = 0;
    List<WorkPeriod> selected = Collections15.arrayList();
    Set<LoadedItem> selectedItems = getSelectedArtifacts(sm);
    Pair<Long, Long> range = getDateRange(sm);
    for (WorkPeriod period : data) {
      if (!isPeriodAccepted(period, sm, selectedItems, range))
        continue;
      selected.add(period);
      if (period.isExcluded())
        excluded++;
    }
    Collections.sort(selected);
    List<WorkPeriod> oldSelection = myDetailsTable.getSelectionAccessor().getSelectedItems();
    ModelUtils.syncModel(myDetailsModel, selected, Containers.comparablesComparator());
    if (!oldSelection.isEmpty())
      myDetailsTable.getSelectionAccessor().setSelected(oldSelection);

    StringBuilder b = new StringBuilder("Showing ");
    if (selected.size() == 0)
      b.append("details ");
    else
      b.append(selected.size()).append(" ").append(English.getSingularOrPlural("record", selected.size())).append(' ');
    b.append("for ");
    if (sm.getSelectionType() == AGridSelectionModel.NOTHING_SELECTED) {
      b.append("all ").append(Terms.ref_artifacts);
    } else if (sm.getRowFrom() == sm.getRowTo() && sm.getRowFrom() >= 0 && sm.getRowFrom() < myTasks.getSize()) {
      TaskEntry entry = myTasks.getAt(sm.getRowFrom());
      if (entry instanceof ArtifactTaskEntry) {
        b.append(Terms.ref_artifact).append(" ").append(((ArtifactTaskEntry) entry).getKey());
      } else if (entry instanceof GroupTaskEntry) {
        b.append(((GroupTaskEntry) entry).getGroupValue() == TOTAL_KEY ? "all" : String.valueOf(entry));
        b.append(" ").append(Terms.ref_artifacts);
      }
    } else {
      b.append("multiple ").append(Terms.ref_artifacts);
    }
    if (sm.getSelectionType() != AGridSelectionModel.NOTHING_SELECTED && sm.getColumnFrom() >= 0 &&
      sm.getColumnTo() < myDateEntries.size())
    {
      TimeZone tz = TimeZone.getDefault();
      long from = DateUtil.truncDay(myDateEntries.get(sm.getColumnFrom()).getStart(), tz);
      long to = DateUtil.truncDay(myDateEntries.get(sm.getColumnTo()).getEnd() - Const.HOUR, tz);
      if (from == to)
        b.append(" on day ").append(DateUtil.LOCAL_DATE.format(new Date(from)));
      else
        b.append(" during ")
          .append(DateUtil.LOCAL_DATE.format(new Date(from)))
          .append("\u2014")
          .append(DateUtil.LOCAL_DATE.format(new Date(to)));
    }

    if (excluded > 0) {
      b.append(" (").append(excluded).append(" excluded)");
    }
    myDetailsLabel.setText(Local.parse(b.toString()));
  }

  private Pair<Long, Long> getDateRange(AGridSelectionModel sm) {
    if (sm.getSelectionType() == AGridSelectionModel.NOTHING_SELECTED)
      return null;
    int rfrom = sm.getRowFrom();
    int rto = sm.getRowTo();
    int cfrom = sm.getColumnFrom();
    int cto = sm.getColumnTo();
    if (rfrom > rto || cfrom > cto)
      return null;
    long min = Long.MAX_VALUE;
    long max = Long.MIN_VALUE;
    for (int c = cfrom; c <= cto; c++) {
      DateEntry entry = myDateEntries.get(c);
      min = Math.min(min, entry.getStart());
      max = Math.max(max, entry.getEnd());
    }
    return Pair.create(min, max);
  }

  private Set<LoadedItem> getSelectedArtifacts(AGridSelectionModel sm) {
    if (sm.getSelectionType() == AGridSelectionModel.NOTHING_SELECTED)
      return null;
    int from = sm.getRowFrom();
    int to = sm.getRowTo();
    if (from > to)
      return null;
    HashSet<LoadedItem> set = Collections15.hashSet();
    for (int i = Math.max(0, from); i <= Math.min(to, myTasks.getSize() - 1); i++) {
      addArtifactsToSet(myTasks.getAt(i), set);
    }
    return set;
  }

  private void addArtifactsToSet(TaskEntry entry, Set<LoadedItem> set) {
    if (entry instanceof ArtifactTaskEntry) {
      set.add(((ArtifactTaskEntry) entry).getArtifact());
    } else if (entry instanceof GroupTaskEntry) {
      for (TaskEntry child : ((GroupTaskEntry) entry).getChildren()) {
        addArtifactsToSet(child, set);
      }
    }
  }

  private boolean isPeriodAccepted(WorkPeriod period, AGridSelectionModel sm, Set<LoadedItem> selectedItems,
    Pair<Long, Long> range)
  {
    if (sm.getSelectionType() == AGridSelectionModel.NOTHING_SELECTED)
      return true;
    if (selectedItems == null || range == null)
      return false;
    if (!selectedItems.contains(period.getArtifact()))
      return false;
    TaskTiming timing = period.getTiming();
    if (timing.getStarted() >= range.getSecond() || timing.getStopped() <= range.getFirst())
      return false;
    return true;
  }

  private void setupCells() {
    myGrid.setCellModel(Lifespan.FOREVER, new AGridCellFunction<TaskEntry, DateEntry, Long>() {
      @Override
      public Long getValue(TaskEntry row, DateEntry column, int rowIndex, int columnIndex) {
        return getCachedTimeInfo(row, column);
      }
    });
    myGrid.setCellRenderer(new LabelRenderer<Long>() {
      {
        myLabel.setHorizontalAlignment(JLabel.RIGHT);
      }

      @Override
      protected void setElement(Long item, CellState state) {
        if (!state.isSelected()) {
          int row = state.getCellRow();
          int column = state.getCellColumn();
          if (row >= 0 && row < myTasks.getSize() && column >= 0 && column < myDateEntries.size()) {
            TaskEntry e = myTasks.getAt(row);
            DateEntry dateEntry = myDateEntries.get(column);
            Color bg = null;
            if (dateEntry.isWeekend()) {
              bg = myWeekendColor;
            } else if (dateEntry.getLevel() == Integer.MAX_VALUE || e.getDepth() == 0) {
              bg = getSummaryColor(3);
            } else if (dateEntry.getLevel() > 0) {
              bg = getSummaryColor(1);
            } else {
              bg = getBackground(e);
            }
            if (bg != null) {
              myLabel.setBackground(bg);
            }
          }
        }
        if (item != null && item > 0) {
          myLabel.setText(DateUtil.getHoursDurationFixed((int) (item / 1000L)));
        } else {
          myLabel.setText("");
        }
      }
    });
  }

  private Color getBackground(TaskEntry e) {
    if (e instanceof GroupTaskEntry) {
      return getSummaryColor(e.getDepth() == 0 ? 3 : 1);
    }
    return null;
  }

  private Color getSummaryColor(int colorGrade) {
    if (colorGrade < 0)
      colorGrade = 0;
    if (colorGrade >= myColorGrades.length)
      colorGrade = myColorGrades.length - 1;
    if (myColorGrades[colorGrade] == null) {
      myColorGrades[colorGrade] =
        ColorUtil.between(UIUtil.getEditorBackground(), Color.YELLOW, 0.11F * (colorGrade + 1));
    }
    return myColorGrades[colorGrade];
  }

  private void setupGroupings() {
    myGroupings.setValue(myCustomizer.getGroupingFunctions());
  }

  private Icon getIndentIcon(int depth) {
    Icon icon = myIndentIconCache.get(depth);
    if (icon != null)
      return icon;
    icon = new EmptyIcon(depth * 12, 1);
    myIndentIconCache.put(depth, icon);
    return icon;
  }

  private Long getCachedTimeInfo(TaskEntry row, DateEntry column) {
    Map<DateEntry, Long> rowmap = myCache.get(row);
    if (rowmap == null) {
      rowmap = Collections15.hashMap();
      myCache.put(row, rowmap);
    }
    if (rowmap.containsKey(column)) {
      // can be null
      return rowmap.get(column);
    }
    Long info = calculateTimeInfo(row, column);
    rowmap.put(column, info);
    return info;
  }

  private Long calculateTimeInfo(TaskEntry row, DateEntry column) {
    if (row instanceof GroupTaskEntry) {
      long total = 0;
      List<TaskEntry> children = ((GroupTaskEntry) row).getChildren();
      for (TaskEntry child : children) {
        Long r = getCachedTimeInfo(child, column);
        if (r != null && r > 0)
          total += r;
      }
      return total;
    } else {
      assert row instanceof ArtifactTaskEntry;
      LoadedItem a = ((ArtifactTaskEntry) row).getArtifact();
      long r = 0;
      long from = column.getStart();
      long to = column.getEnd();
      for (WorkPeriod period : myData.getWorkList()) {
        if (period.isExcluded())
          continue;
        if (!Util.equals(period.getArtifact(), a))
          continue;
        TaskTiming timing = period.getTiming();
        long started = timing.getStarted();
        long stopped = timing.getStopped();
        if (stopped >= from && started < to) {
          long s = Math.max(started, from);
          long f = Math.min(stopped, to);
          if (s < f) {
            r += f - s;
          }
        }
      }
      return r;
    }
  }

  private void setupColumns() {
    long mint = Long.MAX_VALUE, maxt = Long.MIN_VALUE;
    for (WorkPeriod p : myData.getWorkList()) {
      mint = Math.min(mint, p.getTiming().getStarted());
      maxt = Math.max(maxt, p.getTiming().getStopped() - 1000);
    }
    if (okForTiming(mint, maxt)) {
      myDateEntries = createDateEntries(mint, maxt);
      myGrid.setColumnModel(Lifespan.FOREVER, FixedListModel.create(myDateEntries));
      myGrid.setColumnHeaderRenderer(new MyColumnHeaderRenderer());
      myGrid.setColumnHeaderPaintsGrid(false);
      myGrid.setEqualColumnWidthByHeaderAndCellRenderer(15);
    }
  }

  private List<DateEntry> createDateEntries(long from, long to) {
    TimeZone tz = TimeZone.getDefault();
    Calendar cal = Calendar.getInstance(tz, Locale.getDefault());
    List<DateEntry> dates = Collections15.arrayList();
    fillFromTo(dates, from, to, tz, cal);

    int firstDay = cal.getFirstDayOfWeek();
    expandStartToFullWeek(dates, firstDay, tz, cal);

    int lastDay = firstDay == Calendar.SUNDAY ?
      Calendar.SATURDAY : (firstDay == Calendar.MONDAY ? Calendar.SUNDAY : firstDay - 1);
    expandEndToFullWeek(dates, lastDay, tz, cal);

    addWeeks(dates, firstDay, lastDay, cal);

    dates.add(new DateEntry(
      dates.get(0).getStart(), dates.get(dates.size() - 1).getEnd(), "Total", Integer.MAX_VALUE, false));

    return dates;
  }

  private void addWeeks(List<DateEntry> dates, int firstDay, int lastDay, Calendar cal) {
    long weekStart = 0;
    int offset = 0;
    for (int i = 0; i < dates.size(); i++) {
      DateEntry entry = dates.get(i);
      cal.setTimeInMillis(entry.getStart());
      int dow = cal.get(Calendar.DAY_OF_WEEK);
      entry.setWeekOffset(offset++);
      if (dow == firstDay) {
        weekStart = entry.getStart();
      } else if (dow == lastDay) {
        assert weekStart > 0;
        dates.add(i + 1, new DateEntry(weekStart, entry.getEnd(),
          MONTH_FORMAT.format(new Date(weekStart)) + "    Week " + cal.get(Calendar.WEEK_OF_YEAR), 1, false));
        offset = 0;
        i++;
      }
    }
  }

  private static void expandEndToFullWeek(List<DateEntry> dates, int lastDay, TimeZone tz, Calendar cal) {
    long s = dates.get(dates.size() - 1).getEnd();
    if(s <= 0L) {
      Log.warn("Invalid end time " + s + " " + dates);
      assert false;
    }
    while (true) {
      cal.setTimeInMillis(s - H2);
      if (cal.get(Calendar.DAY_OF_WEEK) == lastDay)
        break;
      long e = DateUtil.truncDay(s + Const.DAY + H2, tz);
      cal.setTimeInMillis(s);
      dates.add(new DateEntry(s, e, DAY_FORMAT.format(new Date(s)), 0, isNonworkingDay(cal)));
      s = e;
    }
  }

  private static void expandStartToFullWeek(List<DateEntry> dates, int firstDay, TimeZone tz, Calendar cal) {
    long t = dates.get(0).getStart();
    if(t <= 0L) {
      Log.warn("Invalid start time " + t + " " + dates);
      assert false;
    }
    while (true) {
      cal.setTimeInMillis(t);
      if (cal.get(Calendar.DAY_OF_WEEK) == firstDay)
        return;
      long f = DateUtil.truncDay(t - H2, tz);
      cal.setTimeInMillis(f);
      dates.add(0, new DateEntry(f, t, DAY_FORMAT.format(new Date(f)), 0, isNonworkingDay(cal)));
      t = f;
    }
  }

  private static void fillFromTo(List<DateEntry> dates, long from, long to, TimeZone tz, Calendar cal) {
    from = DateUtil.truncDay(from, tz);
    to = DateUtil.truncDay(to, tz);
    for (long t = from; t <= to;) {
      long e = DateUtil.truncDay(t + Const.DAY + H2, tz);
      cal.setTimeInMillis(t);
      dates.add(new DateEntry(t, e, DAY_FORMAT.format(new Date(t)), 0, isNonworkingDay(cal)));
      t = e;
    }
  }

  private static boolean isNonworkingDay(Calendar cal) {
    int dow = cal.get(Calendar.DAY_OF_WEEK);
    return dow == Calendar.SUNDAY || dow == Calendar.SATURDAY;
  }

  private void setupRows() {
    myTasksSourceModel.clear();
    myTasksSourceModel.addAll(myData.getArtifacts());

    ChangeListener listener = new ChangeListener() {
      public void onChange() {
        resyncTaskModel();
      }
    };
    myTasksSourceModel.addAWTChangeListener(listener);
    myGroupings.addAWTChangeListener(listener);
    resyncTaskModel();
    myGrid.setRowModel(myLife, myTasks);
    myGrid.setRowHeaderRenderer(new LabelRenderer<TaskEntry>() {
      @Override
      protected void setElement(TaskEntry element, CellState state) {
        myLabel.setText(element.toString());
        Color bg = getBackground(element);
        if (bg != null)
          myLabel.setBackground(bg);
        myLabel.setIcon(getIndentIcon(element.getDepth()));
      }
    });
  }

  private void resyncTaskModel() {
    List<GroupingFunction> groupings = myGroupings.getValue();
    GroupTaskEntry total = new GroupTaskEntry(null, TOTAL_KEY);
    List<TaskEntry> r;
    if (groupings != null) {
      r = createGroupedList(groupings, total);
    } else {
      r = Collections15.arrayList();
      r.add(total);
      for (LoadedItem item : myTasksSourceModel) {
        r.add(new ArtifactTaskEntry(total, item));
      }
    }
    for (TaskEntry taskEntry : r) {
      if (taskEntry instanceof ArtifactTaskEntry) {
        myRowDepth = taskEntry.getDepth();
      }
    }
    Collections.sort(r, myTaskEntryComparator);
    ModelUtils.syncModel(myTasks, r, myTaskEntryComparator);
  }

  private int compareArtifactEntries(ArtifactTaskEntry e1, ArtifactTaskEntry e2) {
    // todo - something more obvious
    if (e1 == e2)
      return 0;
    int r = e1.getKey().compareToIgnoreCase(e2.getKey());
    return r != 0 ? r :
      Containers.compareLongs(e1.getArtifact().getItem(), e2.getArtifact().getItem());
  }

  private List<TaskEntry> createGroupedList(List<GroupingFunction> groupings, GroupTaskEntry total) {
    List<TaskEntry> r = Collections15.arrayList();
    r.add(total);
    List<Object[]> table = Collections15.arrayList();
    for (LoadedItem item : myTasksSourceModel) {
      Object[] v = new Object[groupings.size() + 1];
      for (int i = 0; i < groupings.size(); i++) {
        GroupingFunction grouping = groupings.get(i);
        ItemKey key = grouping.getGroupValue(item);
        if (key == null) {
          Log.warn("grouping " + grouping + " null for " + item);
          key = ItemKey.INVALID;
        }
        v[i] = key;
      }
      v[groupings.size()] = item;
      table.add(v);
    }
    for (int i = groupings.size() - 1; i >= 0; i--) {
      Collections.sort(table, taskGroupingComparator(i));
    }
    boolean[] diffGroups = new boolean[groupings.size()];
    for (int i = 0; i < diffGroups.length; i++) {
      diffGroups[i] = hasDifferences(table, i);
    }
    addGroups(r, table, diffGroups, 0, table.size(), 0, groupings.size(), total);
    return r;
  }

  private boolean hasDifferences(List<Object[]> table, int i) {
    ItemKey first = null;
    for (Object[] a : table) {
      if (first == null)
        first = (ItemKey) a[i];
      else if (!first.equals(a[i])) {
        return true;
      }
    }
    return false;
  }

  private void addGroups(List<TaskEntry> target, List<Object[]> table, boolean[] diffGroups, int from, int to,
    int tableDepth, int leafDepth, GroupTaskEntry parent)
  {
    if (from >= to)
      return;
//    int visibleDepth = parent == null ? 0 : parent.getDepth() + 1;
    if (tableDepth == leafDepth) {
      for (int i = from; i < to; i++) {
        target.add(new ArtifactTaskEntry(parent, (LoadedItem) table.get(i)[tableDepth]));
      }
    } else if (!diffGroups[tableDepth]) {
      addGroups(target, table, diffGroups, from, to, tableDepth + 1, leafDepth, parent);
    } else {
      int groupIndex = from;
      while (groupIndex < to) {
        ItemKey groupValue = (ItemKey) table.get(groupIndex)[tableDepth];
        GroupTaskEntry p = new GroupTaskEntry(parent, groupValue);
        target.add(p);
        int n;
        for (n = groupIndex; n < to; n++) {
          if (!groupValue.equals(table.get(n)[tableDepth])) {
            break;
          }
        }
        addGroups(target, table, diffGroups, groupIndex, n, tableDepth + 1, leafDepth, p);
        groupIndex = n;
      }
    }
  }

  private Comparator<? super Object[]> taskGroupingComparator(final int i) {
    return new Comparator<Object[]>() {
      public int compare(Object[] o1, Object[] o2) {
        ItemKey k1 = (ItemKey) o1[i];
        ItemKey k2 = (ItemKey) o2[i];
        return k1.compareTo(k2);
      }
    };
  }

  public JComponent getComponent() {
    return mySplitPane;
//    return myGrid.getComponent();
  }

  public void dispose() {
    myLife.detach();
  }

  private void refreshData() {
    myCache.clear();
    myGrid.getComponent().repaint();
    updateTableFromSelection();
    int[] selection = myDetailsTable.getSelectionAccessor().getSelectedIndexes();
    if (selection != null && selection.length > 0) {
      for (int index : selection) {
        myDetailsModel.forceUpdateAt(index);
      }
    }
    myDetailsTable.repaint();
  }

  private class TaskEntryComparator implements Comparator<TaskEntry> {
    public int compare(TaskEntry o1, TaskEntry o2) {
      if (o1 == o2)
        return 0;
      if (o1 == null || o2 == null) {
        assert false : o1 + " " + o2;
        return o1 == null ? -1 : 1;
      }
      int d1 = o1.getDepth();
      int d2 = o2.getDepth();
      if (d1 < d2)
        return compare(o1, o2.getParent());
      if (d2 < d1)
        return compare(o1.getParent(), o2);
      if (o1.getParent() != o2.getParent())
        return compare(o1.getParent(), o2.getParent());
      if (o1 instanceof GroupTaskEntry) {
        assert o2 instanceof GroupTaskEntry;
        return ((GroupTaskEntry) o1).getGroupValue().compareTo(((GroupTaskEntry) o2).getGroupValue());
      }
      assert o1 instanceof ArtifactTaskEntry;
      assert o2 instanceof ArtifactTaskEntry;
      return compareArtifactEntries(((ArtifactTaskEntry) o1), ((ArtifactTaskEntry) o2));
    }
  }

  private class MyColumnHeaderRenderer extends BaseRendererComponent implements CollectionRenderer<DateEntry> {
    private final int ROWS = 2;
    private DateEntry myEntry;
    private CellState myState;
    private int myRowHeight;

    private Dimension psize = new Dimension();
    private Rectangle pviewR = new Rectangle();
    private Rectangle ptextR = new Rectangle();
    private Rectangle piconR = new Rectangle();
    private Color myBackground;
    private final FontRenderContext myFrc = new FontRenderContext(null, false, false);

    private MyColumnHeaderRenderer() {
      myRowHeight = UIManager.getInt("Table.rowHeight");
      if (myRowHeight == 0)
        myRowHeight = 14;
      setFont(UIManager.getFont("Label.font"));
      setForeground(UIManager.getColor("Label.foreground"));
      setOpaque(true);
      setBackground(UIUtil.getEditorBackground());
    }

    public JComponent getRendererComponent(CellState state, DateEntry item) {
      myState = state;
      myEntry = item;
      return this;
    }

    @Override
    public Dimension getPreferredSize() {
      if (myEntry == null || myEntry.getLevel() > 0)
        return new Dimension(5, 5);
      Rectangle2D bounds = getFont().getStringBounds(myEntry.getName(), myFrc);
      return new Dimension((int) bounds.getWidth() + 4, myRowHeight * ROWS);
    }

    @Override
    protected void paintComponent(Graphics g) {
      AwtUtil.applyRenderingHints(g);
      getSize(psize);
      g.setColor(getBackground());
      g.fillRect(0, 0, psize.width, psize.height);
      if (myEntry == null)
        return;
      Dimension spacing = myGrid.getGridSpacing();
      int y = psize.height - myRowHeight;
      Font f = getFont();
      FontMetrics fm = getFontMetrics(f);
      paintLowestLevel(g, y, psize.height, psize.width, spacing, f, fm);
      int y2 = y;
      y -= myRowHeight;
      paintWeek(g, y, y2, psize.width, spacing, f, fm);
      y2 = y;
      y -= myRowHeight;
    }

    private void paintWeek(Graphics g, int y, int y2, int width, Dimension spacing, Font f, FontMetrics fm) {
      g.setColor(getSummaryColor(myEntry.getLevel() == Integer.MAX_VALUE ? 3 : 1));
      g.fillRect(0, y, width, y2 - y);
      g.setColor(myGrid.getGridColor());
      if (myEntry.getLevel() == 0) {
        g.fillRect(0, y2 - 1, width, spacing.height);
        int ofs = myEntry.getWeekOffset();
        if (ofs >= 0 && myDateEntries != null) {
          int i = myDateEntries.indexOf(myEntry);
          if (i >= 0) {
            DateEntry week = null;
            for (i++; i < myDateEntries.size(); i++) {
              DateEntry e = myDateEntries.get(i);
              if (e.getLevel() == 1) {
                week = e;
                break;
              }
            }
            if (week != null) {
              int x = -ofs * width + 2;
              Rectangle2D bounds = f.getStringBounds(week.getName(), myFrc);
              if (x + bounds.getWidth() > 0) {
                g.setColor(getForeground());
                g.setFont(f);
                int dy = Math.max(0, (y2 - y - fm.getAscent() - fm.getDescent()) / 2);
                g.drawString(week.getName(), x, y + dy + fm.getAscent());
              }
            }
          }
        }
      } else {
        g.fillRect(width - 1, y, spacing.width, y2 - y);
      }
    }

    private void paintLowestLevel(Graphics g, int y, int height, int width, Dimension spacing, Font f, FontMetrics fm) {
      if (myEntry.isWeekend()) {
        g.setColor(myWeekendColor);
        g.fillRect(0, y, width, height - y);
      } else if (myEntry.getLevel() > 0) {
        g.setColor(getSummaryColor(myEntry.getLevel() == Integer.MAX_VALUE ? 3 : 1));
        g.fillRect(0, y, width, height - y);
      }
      int level = myEntry.getLevel();
      if (level == 0) {
        pviewR.setBounds(0, y, width, height - y);
        piconR.setBounds(0, 0, 0, 0);
        ptextR.setBounds(0, 0, 0, 0);
        String s =
          SwingUtilities.layoutCompoundLabel(this, fm, myEntry.getName(), null, CENTER, CENTER, 0, 0, pviewR, piconR,
            ptextR, 0);
        g.setFont(f);
//        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(getForeground());
        g.drawString(s, ptextR.x, ptextR.y + fm.getAscent());
      } else if (level == Integer.MAX_VALUE) {
        pviewR.setBounds(0, y, width, height - y);
        piconR.setBounds(0, 0, 0, 0);
        ptextR.setBounds(0, 0, 0, 0);
        String s =
          SwingUtilities.layoutCompoundLabel(this, fm, "", Icons.TOTAL_SIGMA, CENTER, CENTER, 0, 0, pviewR, piconR,
            ptextR, 0);
//        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Icons.TOTAL_SIGMA.paintIcon(this, g, piconR.x, piconR.y);
      }
      Color c = myGrid.getGridColor();
      g.setColor(c);
      g.fillRect(0, height - 1, width, spacing.height);
      g.fillRect(width - 1, y, spacing.width, height - y);
    }
  }

  static boolean okForTiming(long from, long to) {
    return from > 0 && to > 0 && from < to && (to - from) >= TimeTrackingUtil.MINIMAL_INTERVAL;
  }

  private class EditWorkPeriodAction extends SimpleAction {
    public EditWorkPeriodAction() {
      super("&Edit", Icons.ACTION_WORKLOG_EDIT);
      watchRole(WorkPeriod.ROLE);
      setDefaultPresentation(PresentationKey.ENABLE, EnableState.DISABLED);
    }

    protected void customUpdate(UpdateContext context) throws CantPerformException {
      context.setEnabled(context.getSourceObject(WorkPeriod.ROLE) != null);
    }

    @Override
    protected void doPerform(ActionContext context) throws CantPerformException {
      WorkPeriod period = context.getSourceObject(WorkPeriod.ROLE);
      EditWorkPeriodForm form = new EditWorkPeriodForm();
      form.setArtifacts(myTasksSourceModel, period.getArtifact());
      TaskTiming timing = period.getTiming();
      form.setDates(timing.getStarted(), timing.getStopped());
      form.setComments(timing.getComments());
      form.setCurrent(timing.isCurrent());

      boolean r = showEditWorkForm(context, form, "Edit Work Period");
      if (r) {
        LoadedItem a = form.getSelectedArtifact();
        long from = form.getFrom();
        long to = form.getTo();
        String comments = Util.NN(form.getComments());
        if (a != null && okForTiming(from, to)) {
          WorkPeriod replacement = new WorkPeriod(new TaskTiming(from, to, comments), a);
          if (period.isExcluded())
            replacement.setExcluded(true);
          myData.replace(period, replacement);
          refreshData();
        }
      }
    }
  }

  private class AddWorkPeriodAction extends EnabledAction {
    public AddWorkPeriodAction() {
      super("&Add", Icons.ACTION_WORKLOG_ADD);
    }

    @Override
    protected void doPerform(ActionContext context) throws CantPerformException {
      long time = suggestTime(context);
      List<WorkPeriod> work = myData.getWorkList();
      int index = findPreceding(work, time);
      long from = time;
      long to = time;
      LoadedItem a = null;
      if (index >= 0 && index < work.size()) {
        from = work.get(index).getTiming().getStopped();
        WorkPeriod p = work.get(index);
        a = p.getArtifact();
        if (index + 1 < work.size()) {
          to = work.get(index + 1).getTiming().getStopped();
        }
      }
      EditWorkPeriodForm form = new EditWorkPeriodForm();
      form.setArtifacts(myTasksSourceModel, a);
      form.setDates(from, to);
//        form.setOtherWork(work);
      boolean r = showEditWorkForm(context, form, "Add Work Period");
      if (r) {
        a = form.getSelectedArtifact();
        from = form.getFrom();
        to = form.getTo();
        String comments = Util.NN(form.getComments());
        if (a != null && okForTiming(from, to)) {
          myData.addNew(new WorkPeriod(new TaskTiming(from, to, comments), a));
          refreshData();
        }
      }
    }
  }

  private class DeleteWorkPeriodAction extends SimpleAction {
    public DeleteWorkPeriodAction() {
      super("&Delete", Icons.ACTION_WORKLOG_DELETE);
      setDefaultPresentation(PresentationKey.SHORTCUT, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
      watchRole(WorkPeriod.ROLE);
    }

    @Override
    protected void customUpdate(UpdateContext context) throws CantPerformException {
      final List<WorkPeriod> periods = context.getSourceCollection(WorkPeriod.ROLE);
      boolean enabled = false;
      for(final WorkPeriod p : periods) {
        if(p.getTiming().isCurrent()) {
          enabled = false;
          break;
        }
        enabled = true;
      }
      context.setEnabled(enabled);
    }

    @Override
    protected void doPerform(ActionContext context) throws CantPerformException {
      final List<WorkPeriod> periods = context.getSourceCollection(WorkPeriod.ROLE);
      myData.deleteAll(periods);
      refreshData();
    }
  }
}
