package com.almworks.bugzilla.provider.qb.flags;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ItemKeyStub;
import com.almworks.api.application.qb.*;
import com.almworks.api.engine.ConnectionManager;
import com.almworks.api.engine.Engine;
import com.almworks.api.explorer.gui.TextResolver;
import com.almworks.api.explorer.util.ItemKeys;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.api.syncreg.ItemHypercubeUtils;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.explorer.qbuilder.constraints.AbstractConstraintEditor;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.util.advmodel.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.*;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.Context;
import com.almworks.util.images.Icons;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import com.almworks.util.ui.ColorUtil;
import com.almworks.util.ui.UIUtil;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.util.Collection;
import java.util.List;

import static com.almworks.bugzilla.provider.qb.flags.FlagConstraintDescriptor.*;
import static com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor.SubsetKey;

public class FlagConstraintEditor extends AbstractConstraintEditor {
  private final ItemKey STATUS_ANY = new Anything("<Any Status>");
  private final ItemKey STATUS_PLUS = new Something("+");
  private final ItemKey STATUS_MINUS = new Something("\u2212");
  private final ItemKey STATUS_QUESTION = new Something("?");
  static final ItemKey REQUESTEE_NOBODY = new Nothing("<No Requestee>");

  private JPanel myWholePanel;
  private CompactSubsetEditor<ItemKey> myFlagTypes;
  private ACheckboxList<ItemKey> myStatuses;
  private JScrollPane myStatusesScrollpane;
  private CompactUserSubsetEditor<ItemKey> mySetters;
  private CompactUserSubsetEditor<ItemKey> myRequestees;

  private final EditorKeys myKeys;

  public FlagConstraintEditor(
    ConstraintEditorNodeImpl node, EditorKeys keys,
    BaseEnumConstraintDescriptor ftDescr, BaseEnumConstraintDescriptor seDescr,
    BaseEnumConstraintDescriptor rqDescr, TextResolver userResolver)
  {
    super(node);
    myKeys = keys;

    final Lifespan life = getLifespan();
    final ItemHypercube cube = getContextHypercube();
    initialize(
      ftDescr.getEnumModel(life, cube),
      seDescr.getEnumModel(life, cube),
      rqDescr.getEnumModel(life, cube),
      userResolver);
  }

  private void initialize(
    AListModel<? extends ItemKey> ftModel, AListModel<? extends ItemKey> seModel,
    AListModel<? extends ItemKey> rqModel, TextResolver userResolver)
  {
    setupFlagTypeSubset(ftModel);
    setupStatusList();
    setupUserSubsets(seModel, rqModel, userResolver);
    adjustToPlatform();
    setupBinding();
    setupUiDependencies();
  }

  private void setupFlagTypeSubset(AListModel<? extends ItemKey> types) {
    setupSubsetEditor(myFlagTypes, types, new Anything("<Any Flag>"), null, TYPES_TOKEN);
  }

  private void setupSubsetEditor(
    CompactSubsetEditor<ItemKey> editor, AListModel<? extends ItemKey> model, ItemKey anything, ItemKey nothing, String recentConfig)
  {
    editor.setFullModel(
      prependNothing(model, nothing),
      getRecentsConfig(recentConfig));
    editor.setNothingSelectedItem(anything);
    editor.setCanvasRenderer(User.RENDERER);
    editor.setIdentityConvertor(ItemKey.GET_ID);
    editor.setVisibleRowCount(5);
  }

  private Configuration getRecentsConfig(String name) {
    final BugzillaConnection conn = getContextConnection();
    if(conn == null) {
      return null;
    }
    return conn.getConnectionConfig(BugzillaConnection.RECENTS, "FlagConstraintEditor", name);
  }

  private <T> AListModel<? extends T> prependNothing(AListModel<? extends T> model, T nothing) {
    if(nothing != null) {
      return SegmentedListModel.create(FixedListModel.create(nothing), model);
    }
    return model;
  }

  private void setupStatusList() {
    setStatusListModel();
    makeStatusListLookAligned();
    handleStatusSelectionAndFocus();
  }

  private void setStatusListModel() {
    final AListModel<ItemKey> stsModel = FixedListModel.create(
      STATUS_ANY, STATUS_PLUS, STATUS_MINUS, STATUS_QUESTION);
    myStatuses.setCollectionModel(stsModel);
    myStatuses.setCanvasRenderer(Renderers.defaultCanvasRenderer());
  }

  private void makeStatusListLookAligned() {
    final AToolbarButton btn = new AToolbarButton();
    btn.setIcon(Icons.ACTION_GENERIC_ADD);
    UIUtil.addOuterBorder(myStatusesScrollpane,
      new EmptyBorder(0, 0, 0, btn.getPreferredSize().width));
    myStatusesScrollpane.setOpaque(false);
  }

  private void handleStatusSelectionAndFocus() {
    myStatuses.getScrollable().addFocusListener(
      new SelectionStashingListFocusHandler<ItemKey>(myStatuses.getSelectionAccessor()) {
        @Override
        protected void scrollSelectionToView() {
          myStatuses.scrollListSelectionToView();
        }
      });
  }

  private void setupUserSubsets(
    AListModel<? extends ItemKey> seModel,
    AListModel<? extends ItemKey> rqModel,
    TextResolver userResolver)
  {
    final ItemKey anybody = new Anything("<Anybody>");
    setupSubsetEditor(mySetters, seModel, anybody, null, SETTERS_TOKEN);
    setupSubsetEditor(myRequestees, rqModel, anybody, REQUESTEE_NOBODY, REQUESTEES_TOKEN);
    beginTrackingMyself(seModel, rqModel, userResolver);
  }

  private void beginTrackingMyself(
    final AListModel<? extends ItemKey> seModel,
    final AListModel<? extends ItemKey> rqModel,
    final TextResolver userResolver)
  {
    final BugzillaConnection connection = getContextConnection();
    if(connection != null) {
      final ScalarModel<OurConfiguration> configModel = ((BugzillaContext)connection.getContext()).getConfiguration();
      configModel.getEventSource().addAWTListener(getLifespan(),
        new ScalarModel.Adapter<OurConfiguration>() {
        @Override
        public void onScalarChanged(ScalarModelEvent<OurConfiguration> event) {
          findAndInstallMyselves(event.getNewValue(), seModel, rqModel, userResolver);
        }
      });
    }
  }

  private BugzillaConnection getContextConnection() {
    final ConnectionManager cman = Context.require(Engine.ROLE).getConnectionManager();
    final ItemHypercube cube = getContextHypercube();
    if(cube != null) {
      final Collection<Long> conns = ItemHypercubeUtils.getIncludedConnections(cube);
      if(conns != null && conns.size() == 1) {
        try {
          return (BugzillaConnection)cman.findByItem(conns.iterator().next(), true);
        } catch(InterruptedException e) {
          // no luck, return null;
        } catch(ClassCastException e) {
          // no luck, return null;
        }
      }
    }
    return null;
  }

  private void findAndInstallMyselves(
    OurConfiguration config,
    AListModel<? extends ItemKey> seModel,
    AListModel<? extends ItemKey> rqModel,
    TextResolver userResolver)
  {
    final String username = config.getUserFullEmail();
    if(username == null) {
      mySetters.setMyself(null);
      myRequestees.setMyself(null);
    } else {
      final ItemKey target = userResolver.getItemKey(username);
      mySetters.setMyself(ItemKeys.findInModel(target, seModel));
      myRequestees.setMyself(ItemKeys.findInModel(target, rqModel));
    }
  }

  private void adjustToPlatform() {
    Aqua.disableMnemonics(myWholePanel);
  }

  private void setupBinding() {
    getBinder().setMultipleSelection(myKeys.flagTypesKey, myFlagTypes.getSubsetAccessor());
    getBinder().setMultipleSelection(myKeys.statusesKey, myStatuses.getCheckedAccessor());
    getBinder().setMultipleSelection(myKeys.settersKey, mySetters.getSubsetAccessor());
    getBinder().setMultipleSelection(myKeys.requesteesKey, myRequestees.getSubsetAccessor());
  }

  private void setupUiDependencies() {
    final SelectionAccessor<ItemKey> accessor = myStatuses.getCheckedAccessor();
    final ChangeListener listener = new StatusSelectionTracker(accessor);
    accessor.addAWTChangeListener(getLifespan(), listener);
    listener.onChange();
  }

  private class StatusSelectionTracker implements ChangeListener {
    private final SelectionAccessor<ItemKey> myAccessor;

    private List<ItemKey> myLastSelection;
    private List<ItemKey> myAdded;
    private List<ItemKey> myRemoved;
    private boolean myAdjustingSelection = false;

    public StatusSelectionTracker(SelectionAccessor<ItemKey> accessor) {
      myAccessor = accessor;
    }

    @Override
    public void onChange() {
      updateSelectionHistory();
      if(!myAdjustingSelection) {
        adjustSelection();
        adjustRequesteesAvailability();
      }
    }

    private void updateSelectionHistory() {
      if(myLastSelection == null) {
        myLastSelection = Collections15.arrayList();
      }

      myAdded = myAccessor.getSelectedItems();
      myAdded.removeAll(myLastSelection);

      myRemoved = myLastSelection;
      myLastSelection = myAccessor.getSelectedItems();
      myRemoved.removeAll(myLastSelection);
    }

    private void adjustSelection() {
      myAdjustingSelection = true;
      try {
        if(noneSelected() || anyAdded()) {
          myAccessor.setSelected(STATUS_ANY);
        } else if(allSelected()) {
          myAccessor.addSelection(STATUS_ANY);
        } else {
          myAccessor.removeSelection(STATUS_ANY);
        }
      } finally {
        myAdjustingSelection = false;
      }
    }

    private boolean noneSelected() {
      return !myAccessor.hasSelection();
    }

    private boolean allSelected() {
      return myAccessor.isSelected(STATUS_MINUS)
        && myAccessor.isSelected(STATUS_PLUS) 
        && myAccessor.isSelected(STATUS_QUESTION);
    }

    private boolean anyAdded() {
      return myAdded.contains(STATUS_ANY);
    }

    private void adjustRequesteesAvailability() {
      myRequestees.setEnabled(myAccessor.isSelected(STATUS_QUESTION) || myAccessor.isSelected(STATUS_ANY));
    }
  }

  @Override
  public JComponent getComponent() {
    return myWholePanel;
  }

  @NotNull
  @Override
  public FilterNode createFilterNode(ConstraintDescriptor descriptor) {
    return new ConstraintFilterNode(descriptor,
      FlagConstraintDescriptor.createDescriptorData(
        getFlagTypes(), getStatuses(), getSetters(), getRequestees()));
  }

  private List<ItemKey> getFlagTypes() {
    return nullIfAny(myKeys.flagTypesKey);
  }

  @Nullable
  private List<ItemKey> nullIfAny(SubsetKey key) {
    final List<ItemKey> list = getValue(key);

    if(list == null || list.isEmpty()) {
      return null;
    }

    for(final ItemKey ak : list) {
      if(ak instanceof Anything) {
        return null;
      }
    }

    return list;
  }

  private List<ItemKey> getStatuses() {
    return nullIfAny(myKeys.statusesKey);
  }

  private List<ItemKey> getSetters() {
    return nullIfAny(myKeys.settersKey);
  }

  private List<ItemKey> getRequestees() {
    return nullIfNoQuestion(getStatuses(), nullIfAny(myKeys.requesteesKey));
  }

  private List<ItemKey> nullIfNoQuestion(List<ItemKey> statuses, List<ItemKey> requestees) {
    if(statuses == null || requestees == null || statuses.contains(STATUS_QUESTION)) {
      return requestees;
    }
    return null;
  }

  @Override
  public boolean isModified() {
    return wasChanged(myKeys.toArray());
  }

  @Override
  public void renderOn(Canvas canvas, CellState state, ConstraintDescriptor descriptor) {
    canvas.setIcon(FlagConstraintDescriptor.FLAGS_ICON);
    canvas.appendText(FlagConstraintDescriptor.formatConstraint(
      getFlagTypes(), getStatuses(), getSetters(), getRequestees()));
  }

  static class Special extends ItemKeyStub {
    public Special(String displayName) {
      super(displayName);
    }

    @Override
    public void renderOn(Canvas canvas, CellState state) {
      canvas.setForeground(ColorUtil.between(state.getForeground(), state.getOpaqueBackground(), 0.5f));
      canvas.appendText(getDisplayName());
    }
  }

  static class Nothing extends Special {
    public Nothing(String displayName) {
      super(displayName);
    }
  }

  static class Anything extends Special {
    public Anything(String displayName) {
      super(displayName);
    }
  }

  static class Something extends ItemKeyStub {
    public Something(String displayName) {
      super(displayName);
    }
  }
}
