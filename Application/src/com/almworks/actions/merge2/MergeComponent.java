package com.almworks.actions.merge2;

import com.almworks.actions.CommitStatus;
import com.almworks.api.actions.BaseCommitAction;
import com.almworks.api.application.*;
import com.almworks.api.gui.DefaultCloseConfirmation;
import com.almworks.api.gui.MainMenu;
import com.almworks.edit.EditLifecycleImpl;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.util.L;
import com.almworks.util.advmodel.FilteringListDecorator;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.components.*;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.config.Configuration;
import com.almworks.util.images.Icons;
import com.almworks.util.models.*;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.properties.Role;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.*;
import com.almworks.util.ui.actions.globals.GlobalDataRoot;
import com.almworks.util.ui.actions.presentation.MenuBuilder;
import com.almworks.util.ui.swing.Shortcuts;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;
import util.external.BitSet2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;

class MergeComponent extends SimpleModifiable implements UIComponentWrapper {
  private static final ResolveAction.ApplyLocal TAKE_LOCAL = new ResolveAction.ApplyLocal();
  private static final ResolveAction.ApplyRemote TAKE_REMOTE = new ResolveAction.ApplyRemote();
  private static final ResolveAction.RestoreBase TAKE_ORIGINAL = new ResolveAction.RestoreBase();
  private static final ResolveAction.MarkResolved IGNORE_CONFLICT = new ResolveAction.MarkResolved();

  private static final MenuBuilder RESOLUTIONS;
  static {
    RESOLUTIONS = new MenuBuilder();
    RESOLUTIONS.addDefaultAction(MainMenu.Merge.MANUAL_MERGE_COMMENTS);
    RESOLUTIONS.addAction(TAKE_LOCAL);
    RESOLUTIONS.addAction(TAKE_REMOTE);
    RESOLUTIONS.addAction(TAKE_ORIGINAL);
    RESOLUTIONS.addAction(IGNORE_CONFLICT);
  }

  static final Role<MergeComponent> ROLE = Role.role("mergeComponent");
  private static final String HIDE_SAME = "hideSame";

  private final ATable<KeyMergeState> myTable = ATable.create();
  private final JComponent myMergePanel;
  private final PlaceHolder myBottomPanel = new PlaceHolder();
  private final ItemUiModelImpl myModel;
  private final Configuration myConfiguration;
  private final PropertyMap myBase;
  private final PropertyMap myServer;
  private final JPanel myWholePanel = new JPanel(new BorderLayout());
  private FilteringListDecorator<KeyMergeState> myFieldsModel = null;

  public MergeComponent(Configuration config, ItemUiModelImpl model, PropertyMap base, PropertyMap server) {
    myModel = model;
    myConfiguration = config;
    myBase = base;
    myServer = server;

    myTable.setGridHidden();
    myTable.setStriped(true);
    myTable.getSwingHeader().setReorderingAllowed(false);
    myTable.addGlobalRoles(KeyMergeState.MERGE_STATE);

    final JTable jTable = ((JTable) myTable.getSwingComponent());
    final Dimension intercellSpacing = jTable.getIntercellSpacing();
    intercellSpacing.width = 0;
    jTable.setIntercellSpacing(intercellSpacing);

    final JScrollPane tableScrollPane = myTable.wrapWithScrollPane();
    Aqua.cleanScrollPaneBorder(tableScrollPane);
    Aero.cleanScrollPaneBorder(tableScrollPane);

    myMergePanel = UIUtil.createSplitPane(tableScrollPane, myBottomPanel, false, config, "splitpane", 300);
    Aqua.makeLeopardStyleSplitPane(myMergePanel);
    Aero.makeBorderedDividerSplitPane(myMergePanel);
    myWholePanel.add(myMergePanel, BorderLayout.CENTER);

    GlobalDataRoot.install(myWholePanel);
  }

  private void setData() {
    BitSet2 allKeys = ModelKey.ALL_KEYS.getValue(myModel.getModelMap());
    Set<ModelKey<?>> keys = ModelKeySetUtil.collectSet(allKeys, ModelKey.USER_KEYS);
    final OrderListModel<TableColumnAccessor<KeyMergeState, ?>> columns = OrderListModel.create();
    PropertyMap local = myModel.getLastDBValues();
    final NameColumn nameColumn = new NameColumn();
    columns.addElement(nameColumn);
    columns.addElement(new SimpleColumnAccessor(L.tableColumn("Local Changes"), SideRenderer.create(local)));
    columns.addElement(new StateColumn(false));
    columns.addElement(new SimpleColumnAccessor(L.tableColumn("Original Values"), SideRenderer.create(myBase)));
    columns.addElement(new StateColumn(true));
    columns.addElement(new SimpleColumnAccessor(L.tableColumn("Remote Changes"), SideRenderer.create(myServer)));
    myTable.setColumnModel(columns);

    final List<KeyMergeState> states = Collections15.arrayList(keys.size());
    for (ModelKey<?> key : keys) {
      if (!key.getMergePolicy().autoMerge(key, myModel.getModelMap(), myBase, myServer))
        states.add(new KeyMergeState(key, myModel, myServer, myBase, local));
    }
    Collections.sort(states, nameColumn.getComparator());

    final OrderListModel<KeyMergeState> stateModel = OrderListModel.create(states);
    stateModel.listenElements(states);

    myFieldsModel = FilteringListDecorator.create(stateModel);
    setHideSame(myConfiguration.getBooleanSetting(HIDE_SAME, true));
    myTable.setDataModel(myFieldsModel);
    fireChanged();

    RESOLUTIONS.addToComponent(Lifespan.FOREVER, myTable.getSwingComponent());
  }

  public JComponent getComponent() {
    return myWholePanel;
  }

  public void dispose() {
    myBottomPanel.dispose();
  }

  public void initUI(EditLifecycleImpl editLife) {
    editLife.setupEditModel(myModel, null);
    editLife.setDiscardConfirmation(new DefaultCloseConfirmation(false) {
      protected boolean isCloseConfirmationRequired(ActionContext _) throws CantPerformException {
        return myModel.hasChangesToCommit();
      }

      @Override
      protected String getQuestion() {
        return L.content("Are you sure you want to cancel merge?");
      }
    });
    ElementViewer<ItemUiModel> viewer = myModel.getMetaInfo().createEditor(myConfiguration);
    myBottomPanel.showThenDispose(viewer);
    viewer.showElement(myModel);
    setData();

    ToolbarBuilder builder = ToolbarBuilder.buttonsWithText();
    builder.addAction(CommitMergeAction.INSTANCE);
    builder.addAction(new CancelAction());
    builder.addSeparator();
    builder.addAction(HideSameAction.INSTANCE);
    builder.addSeparator();
    builder.addAction(TAKE_LOCAL, null, makeMapping("Take Local", KeyEvent.VK_L));
    builder.addAction(TAKE_REMOTE, null, makeMapping("Take Remote", KeyEvent.VK_R));
    builder.addAction(TAKE_ORIGINAL, null, makeMapping("Take Original", KeyEvent.VK_O));
    builder.addSeparator();
    builder.addAction(MainMenu.Merge.MANUAL_MERGE_COMMENTS);
    AToolbar toolbar = builder.createHorizontalToolbar();
    CommitStatus.addToToolbar(toolbar);

    JComponent toolbarComponent = viewer.getToolbarEastComponent();

    JPanel topPanel = new JPanel(new BorderLayout(5, 0));
    topPanel.add(toolbar, BorderLayout.CENTER);
    if (toolbarComponent != null) {
      topPanel.add(toolbarComponent, BorderLayout.EAST);
    }
    Aqua.addSouthBorder(topPanel);
    Aero.addLightSouthBorder(topPanel);
    myWholePanel.add(topPanel, BorderLayout.NORTH);

    myTable.getSwingComponent().requestFocusInWindow();
    myTable.getSelectionAccessor().ensureSelectionExists();
    UIUtil.keepSelectionOnRemove(myTable);
  }

  private Map<String, PresentationMapping<?>> makeMapping(String title, int shortcut) {
    final Map<String, PresentationMapping<?>> mapping = Collections15.hashMap();
    mapping.put(Action.SHORT_DESCRIPTION, PresentationMapping.GET_NAME);
    mapping.put(Action.NAME, PresentationMapping.constant(title));
    mapping.put(Action.ACCELERATOR_KEY, PresentationMapping.constant(Shortcuts.ksMenu(shortcut)));
    return mapping;
  }

  public void stop() {
    myBottomPanel.dispose();
  }

  public boolean isHideSame() {
    return KeyMergeState.CHANGED.equals(myFieldsModel.getFilter());
  }

  public void setHideSame(boolean hide) {
    myFieldsModel.setFilter(hide ? KeyMergeState.CHANGED : null);
    myConfiguration.setSetting(HIDE_SAME, hide);
    fireChanged();
  }

  public boolean isDataShown() {
    return myFieldsModel != null;
  }

  private static class StateColumn extends BaseTableColumnAccessor<KeyMergeState, KeyMergeState> {
    private static final Icon EMPTY = EmptyIcon.sameSize(Icons.MERGE_STATE_CONFLICT);
    public StateColumn(final boolean remote) {
      super("", Renderers.createRenderer(new CanvasRenderer<KeyMergeState>() {
        public void renderStateOn(CellState state, com.almworks.util.components.Canvas canvas, KeyMergeState item) {
          canvas.setIcon(getIcon(item, remote));
          canvas.setFullyOpaque(true);
        }
      }));
    }

    public ColumnSizePolicy getSizePolicy() {
      return ColumnSizePolicy.FIXED;
    }

    public int getPreferredWidth(JTable table, ATableModel<KeyMergeState> tableModel,
      ColumnAccessor<KeyMergeState> renderingAccessor, int columnIndex) {
      return EMPTY.getIconWidth();
    }

    public KeyMergeState getValue(KeyMergeState object) {
      return object;
    }

    private static Icon getIcon(KeyMergeState state, boolean remote) {
      boolean localChanged = state.isLocalChanged();
      boolean remoteChanged = state.isRemoteChanged();
      if (localChanged && remoteChanged)
        return remote ? Icons.MERGE_CHANGE_REMOTE_CONFLICT : Icons.MERGE_CHANGE_LOCAL_CONFLICT;
      else if (localChanged)
        return remote ? EMPTY : Icons.MERGE_CHANGE_LOCAL;
      else if (remoteChanged)
        return remote ? Icons.MERGE_CHANGE_REMOTE : EMPTY;
      return EMPTY;
    }
  }

  private static class SideRenderer implements CanvasRenderer<KeyMergeState> {
    private final PropertyMap myValues;

    public SideRenderer(PropertyMap values) {
      myValues = values;
    }

    public void renderStateOn(CellState state, com.almworks.util.components.Canvas canvas, KeyMergeState key) {
      key.renderStateOn(state, canvas, myValues);
    }

    public static CollectionRenderer<KeyMergeState> create(PropertyMap values) {
      return Renderers.createRenderer(new SideRenderer(values));
    }
  }

  private static class CancelAction extends SimpleAction {
    @Override
    protected void customUpdate(UpdateContext context) throws CantPerformException {
      BaseCommitAction.DISCARD.update(context);
      context.putPresentationProperty(PresentationKey.SHORTCUT, Shortcuts.ESCAPE);
    }

    @Override
    protected void doPerform(ActionContext context) throws CantPerformException {
      BaseCommitAction.DISCARD.perform(context);
    }
  }
}
