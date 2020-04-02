package com.almworks.bugzilla.gui.flags.edit;

import com.almworks.api.actions.ItemActionUtils;
import com.almworks.api.application.*;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.bugzilla.provider.datalink.flags2.*;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.util.advmodel.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.*;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.*;
import org.almworks.util.detach.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;

class AddFlagsForm {
  private final Border GAP_WEST = new EmptyBorder(0, UIUtil.GAP, 0, 0);

  private final JPanel myWholePanel = new JPanel(new BorderLayout());
  private final AListModel<EditableFlag> myFlags;
  private final AList<ResolvedItem> myTypes = new AList<ResolvedItem>();
  private ItemHypercube myCube = null;

  AddFlagsForm(AListModel<EditableFlag> editFlags) {
    myFlags = editFlags;

    final AToolbar toolbar = createAddFlagsToolbar();
    configureFlagTypesList(toolbar);
    myWholePanel.add(toolbar, BorderLayout.NORTH);
    myWholePanel.add(createFlagTypesScrollPane(), BorderLayout.CENTER);
    if(!Aqua.isAqua()) {
      myWholePanel.add(Box.createHorizontalStrut(UIUtil.GAP), BorderLayout.WEST);
    }
  }

  private AToolbar createAddFlagsToolbar() {
    final AToolbar toolbar = new AToolbar();

    final JLabel label = new JLabel();
    NameMnemonic.parseString("Add &Flags:").setToLabel(label);
    label.setLabelFor(myTypes.getSwingComponent());
    if(!Aqua.isAqua()) {
      UIUtil.addOuterBorder(label, GAP_WEST);
    }
    toolbar.add(label);

    SeparatorToolbarEntry.INSTANCE.addToToolbar(toolbar);
    toolbar.add(Box.createHorizontalGlue());

    for(final FlagStatus status : new FlagStatus[] { FlagStatus.PLUS, FlagStatus.MINUS, FlagStatus.QUESTION }) {
      final AActionButton button = toolbar.addAction(new AddFlagAction(status));
    }

    Aqua.addSouthBorder(toolbar);
    Aero.addSouthBorder(toolbar);
    makeButtonsAndLabelsAligned(toolbar);

    return toolbar;
  }

  private void makeButtonsAndLabelsAligned(AToolbar toolbar) {
    final Dimension size = UIUtil.getIconButtonPrefSize();
    for(final Component c : toolbar.getComponents()) {
      if(c instanceof AbstractButton) {
        c.setPreferredSize(size);
      } else if(c instanceof JLabel) {
        c.setPreferredSize(new Dimension(c.getPreferredSize().width, size.height));
      }
    }
  }

  private void configureFlagTypesList(final AToolbar toolbar) {
    myTypes.setCanvasRenderer(ItemKey.ICON_NAME_RENDERER);
    SpeedSearchController.install(myTypes);

    final CollectionCommandListener<ResolvedItem> listener = new CollectionCommandListener<ResolvedItem>() {
      @Override
      public void onCollectionCommand(ACollectionComponent<ResolvedItem> comp, int index,
        ResolvedItem element)
      {
        clickFirstButton(toolbar);
      }
    };
    myTypes.addDoubleClickListener(Lifespan.FOREVER, listener);
    myTypes.addKeyCommandListener(Lifespan.FOREVER, listener, KeyEvent.VK_ENTER);
  }

  private void clickFirstButton(AToolbar toolbar) {
    for(int i = 0; i < toolbar.getComponentCount(); i++) {
      final Component c = toolbar.getComponent(i);
      if(c instanceof AbstractButton && c.isEnabled()) {
        ((AbstractButton)c).doClick();
        break;
      }
    }
  }

  private AScrollPane createFlagTypesScrollPane() {
    final AScrollPane scrollPane = new AScrollPane(myTypes);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    if(Aqua.isAqua()) {
      scrollPane.setBorder(Aqua.MAC_BORDER_WEST);
      Aqua.cleanScrollPaneResizeCorner(scrollPane);
    }
    return scrollPane;
  }

  public void setModel(DetachComposite life, final ItemUiModelImpl model) {
    AComponentUtil.keepNotEmptySelection(life, myTypes);
    SegmentedListModel<ResolvedItem> typesModel = createTypesModel(life, model);
    final FilteringListDecorator<? extends ResolvedItem> availableTypes =
      FilteringListDecorator.create(life, typesModel, (Condition<ResolvedItem>) new Condition<ResolvedItem>() {
        @Override
        public boolean isAccepted(ResolvedItem value) {
          if (!(value instanceof FlagTypeItem))
            return false;
          FlagTypeItem type = (FlagTypeItem) value;
          if (type.getTypeId() < 0) return false;
          if (!type.allowsAnyStatus())
            return false;
          if (!type.isSureSingleton())
            return true;
          for (EditableFlag flag : myFlags) {
            FlagTypeItem aType = flag.getType();
            if (aType == null) continue;
            if (aType.getResolvedItem() == type.getResolvedItem())
              return false;
            if (type.getTypeId() < 0)
              continue;
            if (aType.getTypeId() == type.getTypeId())
              return false;
          }
          return true;
        }
      });
    life.add(myTypes.setCollectionModel(availableTypes));
    myTypes.getSelectionAccessor().ensureSelectionExists();
    myFlags.addAWTChangeListener(life, new ChangeListener() {
      @Override
      public void onChange() {
        availableTypes.resynch();
      }
    });
  }

  private SegmentedListModel<ResolvedItem> createTypesModel(final DetachComposite life, final ItemUiModelImpl model) {
    final SegmentedListModel<ResolvedItem> result = SegmentedListModel.create(AListModel.EMPTY);
    ChangeListener cubeListener = new ChangeListener() {
      private final Lifecycle myCycle = new Lifecycle();

      {
        life.add(myCycle.getDisposeDetach());
      }

      @Override
      public void onChange() {
        ItemHypercubeImpl newCube = createCube(model);
        boolean noSegment = result.getSegment(0) == AListModel.EMPTY;
        UIFlagData data = UIFlagData.getInstance(model);
        if (((data == null) == noSegment) && newCube.isSame(myCube))
          return;
        myCycle.cycle();
        myCube = newCube;
        AListModel<? extends ResolvedItem> segment;
        if (data == null)
          segment = AListModel.EMPTY;
        else {
          AListModel<? extends ResolvedItem> allFlagTypes = data.getTypesModel();
          segment = data.getDefaultNarrower().narrowModel(myCycle.lifespan(), allFlagTypes, myCube);
          segment = SortedListDecorator.create(myCycle.lifespan(), segment, ResolvedItem.COMPARATOR);
        }
        result.setSegment(0, segment);
      }
    };
    model.addChangeListener(life, cubeListener);
    cubeListener.onChange();
    return result;
  }

  private static ItemHypercubeImpl createCube(ItemUiModelImpl model) {
    final ModelMap values = model.getModelMap();
    final ItemHypercubeImpl cube = FlagEditor.createConnectionCube(values);
    final ItemKey component = BugzillaKeys.component.getValue(values);
    final ItemKey product = BugzillaKeys.product.getValue(values);
    if(component != null && component.getResolvedItem() > 0) {
      cube.addValue(Bug.attrComponent, component.getResolvedItem(), true);
    } else if (product != null && product.getResolvedItem() > 0) {
      cube.addValue(Bug.attrProduct, product.getResolvedItem(), true);
    }
    return cube;
  }

  public Component getComponent() {
    return myWholePanel;
  }

  private class AddFlagAction extends SimpleAction {
    private final FlagStatus myStatus;

    private AddFlagAction(FlagStatus status) {
      super(status.getDisplayChar());
      myStatus = status;
    }

    @Override
    protected void customUpdate(UpdateContext context) throws CantPerformException {
      SelectionAccessor selection = myTypes.getSelectionAccessor();
      selection.addChangeListener(context.nextUpdateSpan(), context.getUpdateRequest().getChangeListener());
      context.setEnabled(selection.hasSelection() && myCube != null);
      for (ResolvedItem type : myTypes.getSelectionAccessor().getSelectedItems()) {
        if (!((FlagTypeItem) type).allowsStatus(myStatus)) {
          context.setEnabled(EnableState.DISABLED);
          break;
        }
      };
    }

    @Override
    protected void doPerform(ActionContext context) throws CantPerformException {
      java.util.List<ResolvedItem> types = myTypes.getSelectionAccessor().getSelectedItems();
      ModelMap map =  ItemActionUtils.getModel(context).getModelMap();
      for (ResolvedItem type : types) {
        if (!(type instanceof FlagTypeItem)) continue;
        FlagsModelKey.createFlag(map, (FlagTypeItem) type, myStatus);
      }
    }
  }
}
