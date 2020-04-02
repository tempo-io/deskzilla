package com.almworks.bugzilla.gui.flags.edit;

import com.almworks.api.application.ModelMap;
import com.almworks.api.gui.MainMenu;
import com.almworks.api.gui.WindowController;
import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.api.syncreg.ItemHypercubeUtils;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.datalink.flags2.EditableFlag;
import com.almworks.bugzilla.provider.datalink.flags2.FlagsModelKey;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.items.sync.SyncState;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.SegmentedListModel;
import com.almworks.util.commons.Procedure;
import com.almworks.util.commons.Procedure2;
import com.almworks.util.components.*;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.*;
import com.almworks.util.ui.swing.AwtUtil;
import com.almworks.util.ui.swing.SwingTreeUtil;
import com.almworks.util.ui.widgets.*;
import com.almworks.util.ui.widgets.impl.HostComponentState;
import com.almworks.util.ui.widgets.impl.WidgetHostComponent;
import com.almworks.util.ui.widgets.util.*;
import com.almworks.util.ui.widgets.util.list.*;
import org.almworks.util.*;
import org.almworks.util.detach.DetachComposite;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.Map;

public class FlagEditor implements UIComponentWrapper {
  public static final TypedKey<ModelMap> KEY_MODEL = TypedKey.create("model");
  private static final Widget<? super EditableFlag> GAP_BUTTONS = new GapWidget(new Dimension(1, 1));
  private static final Widget<? super EditableFlag> GAP5 = new GapWidget(new Dimension(5, 1));
  private static final Widget<? super EditableFlag> COLUMN_BREAK = new ColumnBreakWidget(10, 5);
  private static final Widget<EditableFlag>[] WIDGETS = createRow();
  private static final ConstHeightZebra TABLE_POLICY = new ConstHeightZebra(2, 2);
  private static final Procedure2<GraphContext,EditableFlag> SHOW_REMOVED_FLAG = new Procedure2<GraphContext, EditableFlag>() {
    @Override
    public void invoke(GraphContext context, EditableFlag flag) {
      if (flag.isDeleted()) context.setColor(removedColor(context));
    }
  };

  @SuppressWarnings({"unchecked"})
  private static Widget<EditableFlag>[] createRow() {
    return new Widget[]{
      GAP5,
      ModificationStateWidget.INSTANCE,
      COLUMN_BREAK,
      SETTER_CELL,
      COLUMN_BREAK,
      TYPE_CELL,
      COLUMN_BREAK,
      StatusCell.PLUS,
      GAP_BUTTONS,
      StatusCell.MINUS,
      GAP_BUTTONS,
      StatusCell.REQ,
      COLUMN_BREAK,
      RequesteeWidget.INSTANCE,
      COLUMN_BREAK,
      StatusCell.CLEAR,
      new DiscardButton(),
      GAP5
    };
  }

  private final WidgetHostComponent myFlagsHost = new WidgetHostComponent();
  private final HostComponentState<AListModel<EditableFlag>> myState = myFlagsHost.createState();
  private final JComponent myComponent;
  private final DetachComposite myLife = new DetachComposite();
  private final SegmentedListModel<EditableFlag> myEditFlags = SegmentedListModel.<EditableFlag>create(myLife, AListModel.EMPTY);
  private final AddFlagsForm myForm = new AddFlagsForm(myEditFlags);

  private FlagEditor(boolean standalone) {
    myFlagsHost.setState(myState);
    myFlagsHost.setListenMouseWheel(false);

    final ColumnListWidget<EditableFlag> table = new ColumnListWidget<EditableFlag>(createRow(), TABLE_POLICY,
      TABLE_POLICY);
    table.resetColumnLayout(0, 0, 15);
    table.setColumnPolicy(SETTER_CELL, 0, 5, -1);
    table.setColumnPolicy(TYPE_CELL, 0, 1, -1);
    table.setColumnPolicy(RequesteeWidget.INSTANCE, 2000, 3, new JComboBox().getPreferredSize().width + 5);
    HeaderLayout headerManager = new HeaderLayout();
    table.addLayoutListener(Lifespan.FOREVER, ThreadGate.AWT_IMMEDIATE, headerManager);
    myState.setWidget(table);

    ScrollableAware.COMPONENT_PROPERTY.putClientValue(myFlagsHost, new ScrollableAware() {
      @Override
      public boolean wantFillViewportHeight(Dimension viewport, Dimension preferred) {
        return viewport.height > preferred.height;
      }
      @Override
      public boolean wantFillViewportWidth(Dimension viewport, Dimension preferred) {
        return true;
      }
    });

    final ScrollablePanel scrollable = new ScrollablePanel(myFlagsHost);
    scrollable.setBackground(DocumentFormAugmentor.backgroundColor());
    final JScrollPane flagsScrollPane = new JScrollPane(
      scrollable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    headerManager.configureScrollPane(flagsScrollPane);
    flagsScrollPane.setMinimumSize(new Dimension(table.calcMinWidth() + 20, 100));
    Aqua.cleanScrollPaneBorder(flagsScrollPane);
    Aero.cleanScrollPaneBorder(flagsScrollPane);

    final JComponent editorContent = new JPanel(new BorderLayout());
    editorContent.add(addMainToolbar(flagsScrollPane, standalone), BorderLayout.CENTER);
    editorContent.add(myForm.getComponent(), BorderLayout.EAST);

    myComponent = editorContent;
    myState.setValue(myEditFlags);
  }

  private static JComponent addMainToolbar(JComponent flagArea, boolean standalone) {
    final AToolbar toolbar = new AToolbar();
    if(standalone) {
      toolbar.addAction(MainMenu.ItemEditor.COMMIT);
      toolbar.addAction(MainMenu.ItemEditor.SAVE_DRAFT);
      toolbar.addAction(MainMenu.ItemEditor.DISCARD);
    } else {
      final Map<String, PresentationMapping<?>> map = Collections15.hashMap();
      map.put(Action.NAME, PresentationMapping.constant("Done"));
      map.put(Action.SMALL_ICON, PresentationMapping.constant(Icons.BLUE_TICK));
      map.put(Action.SHORT_DESCRIPTION, PresentationMapping.constant("Close window and return to the bug editor"));
      toolbar.addAction(WindowController.CLOSE_ACTION).overridePresentation(map);
    }
    Aqua.addSouthBorder(toolbar);
    Aero.addSouthBorder(toolbar);

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(toolbar, BorderLayout.NORTH);
    panel.add(flagArea, BorderLayout.CENTER);
    return panel;
  }

  public void setInitial(final ItemUiModelImpl model) throws CantPerformExceptionExplained {
    ConstProvider.addRoleValue(myComponent, ItemUiModelImpl.ROLE, model);
    myState.putWidgetData(KEY_MODEL, model.getModelMap());
    myForm.setModel(myLife, model);
    AListModel<EditableFlag> flagsModel = FlagsModelKey.createEditableModel(myLife, model.getModelMap());
    myEditFlags.setSegment(0, flagsModel);
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void dispose() {
    myLife.detach();
    myFlagsHost.setState(null);
    myState.setWidget(null);
    myState.setValue(null);
  }

  public static Color removedColor(CellContext context) {
    JComponent component = context.getHost().getHostComponent();
    return ColorUtil.between(component.getBackground(), component.getForeground(), 0.5f);
  }

  public static ItemHypercubeImpl createConnectionCube(ModelMap values) {
    BugzillaConnection connection = BugzillaConnection.getInstance(values);
    ItemHypercubeImpl cube = new ItemHypercubeImpl();
    ItemHypercubeUtils.adjustForConnection(cube, connection);
    return cube;
  }

  public static FlagEditor create(ItemUiModelImpl model, boolean standalone) throws CantPerformExceptionExplained {
    FlagEditor editor = new FlagEditor(standalone);
    boolean success = false;
    try {
      editor.setInitial(model);
      success = true;
    } finally {
      if (!success) editor.myLife.detach();
    }
    return editor;
  }

  public static Configuration getRecentConfig(CellContext context, String subsetName) {
    ModelMap map = context.getHost().getWidgetData(KEY_MODEL);
    BugzillaConnection connection = BugzillaConnection.getInstance(map);
    return connection != null ? connection.getConnectionConfig("flagsEditor", subsetName) : Configuration.EMPTY_CONFIGURATION;
  }

  private static final TextLeafCell<EditableFlag> TYPE_CELL = new TextLeafCell<EditableFlag>("flagType", true, Font.BOLD, SHOW_REMOVED_FLAG, EditableFlag.TYPE_NAME);

  private static final TextLeafCell<EditableFlag> SETTER_CELL = new TextLeafCell<EditableFlag>("userName@mycompany.com", true, Font.PLAIN, SHOW_REMOVED_FLAG, EditableFlag.SETTER_DISPLAY);

  private static class DiscardButton extends ButtonCell<EditableFlag> {
    @Override
    protected void actionPerformed(EventContext context, EditableFlag value) {
      ModelMap map = context.getHost().getWidgetData(KEY_MODEL);
      if (isDuplicateFlag(value)) FlagsModelKey.replaceWithNewCopy(map, value);
      else FlagsModelKey.discard(map, value);
      context.repaint();
    }

    @Override
    protected void setupButton(AbstractButton button, boolean selected, EditableFlag value) {
      if (isDuplicateFlag(value)) button.setIcon(Icons.FLAG);
      else button.setIcon(Icons.ACTION_UNDO);
      button.setText("");
    }

    @Override
    protected String getTooltip(EditableFlag flag) {
      if (!isEnabled(flag)) return null;
      if (isDuplicateFlag(flag)) return "Copy your changes as new flag";
      SyncState state = flag.getSyncState();
      switch (state) {
      case SYNC: return null;
      case NEW: return "Undo adding this flag";
      case EDITED:
      case CONFLICT:
      case LOCAL_DELETE: return "Undo your changes";
      case DELETE_MODIFIED: return "Restore this flag";
      case MODIFIED_CORPSE: 
      default:
        Log.error("Unknown editable state state " + state);
        return null;
      }
    }

    private boolean isDuplicateFlag(EditableFlag flag) {
      return flag != null && flag.getSyncState() == SyncState.MODIFIED_CORPSE;
//      return ModificationStateWidget.getFlagState(flag) == ModificationStateWidget.STATE_REMOTE_CLEAR; // DZO-757
    }

    @Override
    protected EnableState getEnableState(EditableFlag flag) {
      SyncState state = flag.getSyncState();
      switch (state) {
      case NEW:
      case EDITED:
      case LOCAL_DELETE:
      case CONFLICT:
      case DELETE_MODIFIED:
      case MODIFIED_CORPSE:
        return EnableState.ENABLED;
      case SYNC:
        return EnableState.INVISIBLE;
      default:
        Log.error("Unknown editable state state " + state);
        return EnableState.INVISIBLE;
      }
    }

    @Override
    public void paint(@NotNull GraphContext context, @Nullable EditableFlag flag) {
      if (isVisible(flag)) paintButton(context, false, true, flag);
    }
  }

  private static class HeaderLayout implements Procedure<HostCell> {
    private final JTable myTable = new JTable();

    private HeaderLayout() {
      DefaultTableModel model = new DefaultTableModel();
      model.addColumn("");
      model.addColumn("Setter");
      model.addColumn("Flag");
      model.addColumn("Status");
      model.addColumn("Requestee");
      model.addColumn("Actions");
      myTable.setModel(model);
      getHeader().setReorderingAllowed(false);
      getHeader().setResizingAllowed(false);
    }

    public JTableHeader getHeader() {
      return myTable.getTableHeader();
    }

    @Override
    public void invoke(HostCell cell) {
      JTableHeader header = getHeader();
      TableColumnModel model = header.getColumnModel();
      int[] widths = ColumnListWidget.copyCurrentColumnLayout(cell);
      header.setVisible(widths != null);
      if (widths == null) return;
      ColumnBreakWidget.ColumnIterator columns = new ColumnBreakWidget.ColumnIterator(WIDGETS, widths);
      JComponent host = cell.getHost().getHostComponent();
      JViewport viewport = SwingTreeUtil.findAncestorOfType(host, JViewport.class);
      if (viewport == null) return;
      Insets insets = AwtUtil.uniteInsetsFromTo(host, viewport);
      model.getColumn(0).setWidth(columns.nextColumnWidth() + insets.left);
      model.getColumn(1).setWidth(columns.nextColumnWidth());
      model.getColumn(2).setWidth(columns.nextColumnWidth());
      model.getColumn(3).setWidth(columns.nextColumnWidth());
      model.getColumn(4).setWidth(columns.nextColumnWidth());
      model.getColumn(5).setWidth(columns.nextColumnWidth() + insets.right);
    }

    public void configureScrollPane(JScrollPane scrollPane) {
      scrollPane.setColumnHeaderView(getHeader());
      scrollPane.setCorner(JScrollPane.UPPER_RIGHT_CORNER, JTableAdapter.createCornerComponent(myTable));
    }
  }
}