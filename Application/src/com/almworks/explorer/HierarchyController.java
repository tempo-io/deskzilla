package com.almworks.explorer;

import com.almworks.api.application.*;
import com.almworks.api.engine.Connection;
import com.almworks.api.explorer.MetaInfoCollector;
import com.almworks.api.explorer.TableController;
import com.almworks.util.advmodel.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.Convertors;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.components.tables.HierarchicalTable;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.ui.SingleChildLayout;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Util;
import org.almworks.util.detach.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.almworks.util.ui.SingleChildLayout.CONTAINER;
import static com.almworks.util.ui.SingleChildLayout.PREFERRED;

class HierarchyController implements UpdateRequestable {
  private static final String ARTIFACT_TREE_LAYOUT = "ArtifactTreeLayout";
  private ItemsTreeLayout myTreeLayout = ItemsTreeLayout.NONE;
  private final SimpleProvider myProvider;
  @Nullable
  private volatile Configuration myConfig;
  private final MetaInfoCollector myCollector;
  private final AtomicReference<String> myLayoutId = new AtomicReference<String>(null);
  private final HierarchicalTable<LoadedItem> myArtifactsTree;
  private final Lifecycle myTreeStructureLife = new Lifecycle();
  private final AComboBox<ItemsTreeLayout> myCombobox = new AComboBox<ItemsTreeLayout>() {
    @Override
    public void addNotify() {
      super.addNotify();
      ComponentUpdateController.connectUpdatable(getDisplayableLifespan(), HierarchyController.this, myCombobox);
      getDisplayableLifespan().add(new Detach() {
        @Override
        protected void doDetach() throws Exception {
          Configuration config = myConfig;
          AComboboxModel<ItemsTreeLayout> model = myCombobox.getModel();
          ItemsTreeLayout selected = model.getSelectedItem();
          if (config != null && selected != null && model.getSize() > 0 && myCombobox.isEnabled())
            setLayoutId(config, selected.getId());
          myCombobox.setEnabled(false);
          myCombobox.setModel(AComboboxModel.EMPTY_COMBOBOX);
          myUpdateLife.cycle();
        }
      });
    }
  };
  private final JPanel myWholePanel = SingleChildLayout.envelop(myCombobox, CONTAINER, PREFERRED, CONTAINER, CONTAINER, 0F, 0.5F);
  private final Lifecycle myUpdateLife = new Lifecycle();
  @Nullable
  private AListModel<? extends LoadedItem> myModel = null;
  @Nullable
  private AListModelUpdater<? extends LoadedItem> myListModelUpdater = null;

  public HierarchyController(SimpleProvider provider, HierarchicalTable<LoadedItem> artifactsTree,
    MetaInfoCollector metaInfoCollector) {
    myProvider = provider;
    myArtifactsTree = artifactsTree;
    myCollector = metaInfoCollector;
    myProvider.setSingleData(ItemsTreeLayout.DATA_ROLE, myTreeLayout);
    myCollector.getMetaInfoModel().addChangeListener(Lifespan.FOREVER, new ChangeListener() {
      public void onChange() {
        if (myConfig != null) readLayoutFromConfig();
      }
    });
    myCombobox.setEnabled(false);
    myCombobox.setFocusable(false);
    myCombobox.setCanvasRenderer(Renderers.canvasDefault(ItemsTreeLayout.NO_HIERARCHY));
    myCombobox.setColumns(ItemsTreeLayout.NO_HIERARCHY.length());

    myCombobox.getModel().addSelectionListener(Lifespan.FOREVER, new SelectionListener.Adapter() {
      public void onSelectionChanged() {
        if (!myCombobox.isEnabled()) return;
        DefaultActionContext context = new DefaultActionContext(myCombobox);
        try {
          ItemsTreeLayout item = myCombobox.getModel().getSelectedItem();
          context.getSourceObject(TableController.DATA_ROLE).setTreeLayout(item);
        } catch (CantPerformException e) {
          assert false : e;
        }
      }
    });
  }

  public void setConfig(ItemCollectionContext contextInfo, Configuration common) {
    setConfig(chooseConfig(contextInfo, common));
  }

  private Configuration chooseConfig(ItemCollectionContext contextInfo, Configuration common) {
    if (contextInfo == null) return common;
    Configuration special = contextInfo.getContextConfig();
    if (special == null) return common;
    if (!special.isSet(ARTIFACT_TREE_LAYOUT))
      setLayoutId(special, getLayoutId(common));
    return special;
  }

  private String getLayoutId(Configuration common) {
    return common.getSetting(ARTIFACT_TREE_LAYOUT, ItemsTreeLayout.NONE.getId());
  }

  private void setConfig(Configuration config) {
    if (config == null) {
      myConfig = null;
      return;
    }
    myLayoutId.set(getLayoutId(config));
    myConfig = config;
    readLayoutFromConfig();
  }

  private void readLayoutFromConfig() {
    String layoutId = myLayoutId.get();
    if (layoutId == null) return;
    if (layoutId.equals(myTreeLayout.getId())) {
      myLayoutId.compareAndSet(layoutId, null);
      return;
    }
    Collection<MetaInfo> metas = myCollector.getAllMetaInfos();
    for (MetaInfo meta : metas)
      for (ItemsTreeLayout layout : meta.getTreeLayouts().toList())
        if (layoutId.equals(layout.getId())) {
          setTreeLayout(layout);
          return;
        }
  }

  public void setTreeLayout(@Nullable ItemsTreeLayout layout) {
    layout = layout != null ? layout : ItemsTreeLayout.NONE;
    String initialId = myLayoutId.get();
    String newLayoutId = layout.getId();
    if (initialId != null && initialId.equals(newLayoutId)) myLayoutId.compareAndSet(initialId, null);
    boolean storeLayout = myLayoutId.get() == null;
    Configuration config = myConfig;
    if (config != null && storeLayout && myCombobox.isEnabled())
      setLayoutId(config, newLayoutId);
    if (Util.equals(myTreeLayout, layout)) return;
    myTreeLayout = layout;
    if (myModel != null) updateTreeRoot();
    myProvider.setSingleData(ItemsTreeLayout.DATA_ROLE, layout);
  }

  private void setLayoutId(Configuration config, String newLayoutId) {
    config.setSetting(ARTIFACT_TREE_LAYOUT, newLayoutId);
  }

  private void updateTreeRoot() {
    myArtifactsTree.setShowRootHanders(!Util.equals(myTreeLayout, ItemsTreeLayout.NONE));
    SelectionAccessor<LoadedItem> accessor = myArtifactsTree.getSelectionAccessor();
    List<LoadedItem> selection = accessor.getSelectedItems();
    assert myModel != null;

    // Create new tree and set root to table before detaching old to speed up detach.
    ListModelTreeAdapter<LoadedItem, TreeModelBridge<LoadedItem>> adapter =
      ListModelTreeAdapter.create(myModel, myTreeLayout.getTreeStructure(), null);
    myArtifactsTree.clearRoot();
    myTreeStructureLife.cycle();
    adapter.attach(myTreeStructureLife.lifespan());
    myArtifactsTree.setRoot(adapter.getRootNode());
    myArtifactsTree.expandAll();
    accessor.setSelected(selection);
    myArtifactsTree.scrollSelectionToView();
  }

  public void setListModelUpdater(Lifespan life, AListModelUpdater<? extends LoadedItem> updater,
    AListModel<? extends LoadedItem> model) {
    myModel = model;
    myListModelUpdater = updater;
    updateTreeRoot();
    life.add(myTreeStructureLife.getDisposeDetach());
  }

  public void loadingDone(Runnable finish) {
    AListModelUpdater<?> updater = myListModelUpdater;
    if (updater == null) {
      finish.run();
    } else {
      updater.runWhenNoPendingUpdates(ThreadGate.AWT, finish);
    }
  }

  public void update(UpdateRequest request) {
    myUpdateLife.cycle();
    request.watchRole(TableController.DATA_ROLE);
    request.watchRole(ItemsTreeLayout.DATA_ROLE);
    request.watchRole(ItemCollectionContext.ROLE);
    TableController controller = request.getSourceObjectOrNull(TableController.DATA_ROLE);
    myCombobox.setEnabled(controller != null);
    if (controller == null) {
      myCombobox.setModel(AComboboxModel.EMPTY_COMBOBOX);
      return;
    }
    request.updateOnChange(controller.getMetaInfoCollector().getMetaInfoModel());
    ItemsTreeLayout item = myCombobox.getModel().getSelectedItem();

    MetaInfoCollector metaInfos = controller.getMetaInfoCollector();
    AListModel<ItemsTreeLayout> allLayoutsModel =
      SegmentedListModel.flatten(myUpdateLife.lifespan(), metaInfos.getMetaInfoModel(), MetaInfo.TREE_LAYOUTS);
    ItemCollectionContext context = request.getSourceObjectOrNull(ItemCollectionContext.ROLE);
    final Connection connectionRestriction = context != null ? context.getSourceConnection() : null;
    AListModel<ItemsTreeLayout> layoutsModel = connectionRestriction == null ? allLayoutsModel :
      FilteringConvertingListDecorator.create(myUpdateLife.lifespan(), allLayoutsModel, new Condition<ItemsTreeLayout>() {
        @Override
        public boolean isAccepted(ItemsTreeLayout layout) {
          return layout.isApplicableTo(connectionRestriction);
        }
      }, Convertors.<ItemsTreeLayout>identity());
    SelectionInListModel<ItemsTreeLayout> model =
      SelectionInListModel.createForever(SegmentedListModel.prepend(ItemsTreeLayout.NONE, layoutsModel), null);
    model.setSelectedItem(model.indexOf(item) > -1 ? item : null);
    myCombobox.setModel(model);
    ItemsTreeLayout layout = request.getSourceObjectOrNull(ItemsTreeLayout.DATA_ROLE);
    model.setSelectedItem(layout != null ? layout : ItemsTreeLayout.NONE);
  }

  public JComponent getComponent() {
    return myWholePanel;
  }
}
