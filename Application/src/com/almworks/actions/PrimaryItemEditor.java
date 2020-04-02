package com.almworks.actions;

import com.almworks.api.actions.*;
import com.almworks.api.application.ItemUiModel;
import com.almworks.api.application.MetaInfo;
import com.almworks.api.gui.MainMenu;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.gui.InitialWindowFocusFinder;
import com.almworks.util.components.AToolbar;
import com.almworks.util.components.PlaceHolder;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.config.Configuration;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.ActionRegistry;
import com.almworks.util.ui.actions.ConstProvider;
import org.almworks.util.detach.Detach;

import javax.swing.*;
import java.awt.*;

/**
 * @author dyoma
 */
class PrimaryItemEditor implements UIComponentWrapper2 {
  private final JPanel myWholePanel = new JPanel(new BorderLayout(0, 0));
  private final PlaceHolder myEditorPlace = new PlaceHolder();
  private final Configuration myConfiguration;
  private final PlaceHolder myViewerToolbarComponentPlaceHolder = new PlaceHolder();
  private final ItemUiModelImpl myModel;
  private final AToolbar myToolbar = new AToolbar(null, InlineLayout.HORISONTAL);

  private PrimaryItemEditor(ItemUiModelImpl model, Configuration configuration) {
    myModel = model;
    myConfiguration = configuration;

    myToolbar.addAction(MainMenu.ItemEditor.COMMIT);
    myToolbar.addAction(MainMenu.ItemEditor.SAVE_DRAFT);
    myToolbar.addAction(MainMenu.ItemEditor.DISCARD);
    CommitStatus.addToToolbar(myToolbar);

    final JPanel top = new JPanel(new BorderLayout());
    top.add(myToolbar, BorderLayout.CENTER);
    top.add(myViewerToolbarComponentPlaceHolder, BorderLayout.EAST);
    Aqua.addSouthBorder(top);
    Aero.addSouthBorder(top);

    myWholePanel.add(top, BorderLayout.NORTH);
    myWholePanel.add(myEditorPlace);

    ConstProvider.addRoleValue(myWholePanel, ItemActionUtils.ITEM_EDIT_WINDOW_TOOLBAR_RIGHT_PANEL,
      myViewerToolbarComponentPlaceHolder);
  }

  private void editModel() {
    MetaInfo metaInfo = myModel.getMetaInfo();
    ElementViewer<ItemUiModel> viewer = metaInfo.createEditor(myConfiguration.getOrCreateSubset(metaInfo.getTypeId()));
    metaInfo.setupEditToolbar(myToolbar);
    myViewerToolbarComponentPlaceHolder.show(viewer.getToolbarEastComponent());
    myEditorPlace.showThenDispose(viewer);
    viewer.showElement(myModel);
    InitialWindowFocusFinder.focusInitialComponent(myWholePanel);
  }

  public JComponent getComponent() {
    return myWholePanel;
  }

  public void dispose() {
    myEditorPlace.dispose();
    myViewerToolbarComponentPlaceHolder.dispose();
  }

  public Detach getDetach() {
    return new Disposer(this);
  }

  public static void registerActions(ActionRegistry registry) {
    registry.registerAction(MainMenu.ItemEditor.COMMIT,
      ItemActionUtils.setupCommitAction(new CommitEditedModelAction(true)));
    registry.registerAction(MainMenu.ItemEditor.SAVE_DRAFT,
      ItemActionUtils.setupSaveAction(new CommitEditedModelAction(false)));
    registry.registerAction(MainMenu.ItemEditor.DISCARD, BaseCommitAction.DISCARD);
  }

  public static PrimaryItemEditor editModel(ItemUiModelImpl model, Configuration config) {
    PrimaryItemEditor editor = new PrimaryItemEditor(model, config);
    editor.editModel();
    return editor;
  }
}
