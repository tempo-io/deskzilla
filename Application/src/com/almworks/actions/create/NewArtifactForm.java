package com.almworks.actions.create;

import com.almworks.actions.CommitStatus;
import com.almworks.api.actions.BaseCommitAction;
import com.almworks.api.actions.ItemActionUtils;
import com.almworks.api.application.ItemUiModel;
import com.almworks.api.edit.ItemCreator;
import com.almworks.api.gui.MainMenu;
import com.almworks.gui.InitialWindowFocusFinder;
import com.almworks.util.L;
import com.almworks.util.components.*;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.ActionRegistry;
import com.almworks.util.ui.actions.ConstProvider;
import org.almworks.util.detach.Detach;

import javax.swing.*;
import java.awt.*;

/**
 * @author dyoma
 */
public class NewArtifactForm implements UIComponentWrapper {
  private final JPanel myWholePanel = new JPanel(new BorderLayout());
  private final PlaceHolder myViewerSelectorPlace = new PlaceHolder();
  private final JComponent myLoadingMessage = UIUtil.createMessage(L.content("Please wait\u2026"));
  private Detach myEditorDetach = Detach.NOTHING;
  private final AToolbar myToolbar;

  public NewArtifactForm() {
    JPanel headerPanel = new JPanel(new BorderLayout());
    myToolbar = createToolbar();
    headerPanel.add(myToolbar, BorderLayout.CENTER);
    headerPanel.add(createSelectorPlace(), BorderLayout.EAST);
    Aqua.addSouthBorder(headerPanel);
    Aero.addSouthBorder(headerPanel);

    myWholePanel.add(headerPanel, BorderLayout.NORTH);
    myWholePanel.add(myLoadingMessage, BorderLayout.CENTER);

    ConstProvider.addRoleValue(myWholePanel, ItemActionUtils.ITEM_EDIT_WINDOW_TOOLBAR_RIGHT_PANEL,
      myViewerSelectorPlace);
  }

  private JComponent createSelectorPlace() {
    return myViewerSelectorPlace;
  }

  public JComponent getComponent() {
    return myWholePanel;
  }

  public void dispose() {
    myEditorDetach.detach();
    myEditorDetach = Detach.NOTHING;
  }

  private AToolbar createToolbar() {
    ToolbarBuilder builder = ToolbarBuilder.buttonsWithText();
    builder.addAction(MainMenu.NewItem.COMMIT);
    builder.addAction(MainMenu.NewItem.SAVE_DRAFT);
    builder.addAction(MainMenu.NewItem.DISCARD);
    AToolbar toolbar = builder.createToolbar(InlineLayout.HORISONTAL);
    CommitStatus.addToToolbar(toolbar);
    return toolbar;
  }

  public void setEditor(ElementViewer<ItemUiModel> editor, ItemCreator creator) {
    assert myEditorDetach == Detach.NOTHING;
    myEditorDetach = new Disposer(editor);
    myWholePanel.remove(myLoadingMessage);
    myWholePanel.add(editor.getComponent());
    creator.setupToolbar(myToolbar);
    myViewerSelectorPlace.show(editor.getToolbarEastComponent());
    InitialWindowFocusFinder.focusInitialComponent(myWholePanel);
  }

  public static void registerActions(ActionRegistry registry) {
    registry.registerAction(MainMenu.NewItem.COMMIT, CreateItemCopyAction.createCommitAction());
    registry.registerAction(MainMenu.NewItem.SAVE_DRAFT, CreateItemCopyAction.createSaveAction());
    registry.registerAction(MainMenu.NewItem.DISCARD, BaseCommitAction.DISCARD);
  }
}
