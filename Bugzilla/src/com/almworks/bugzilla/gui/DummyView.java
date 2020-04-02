package com.almworks.bugzilla.gui;

import com.almworks.api.application.*;
import com.almworks.api.application.util.ValueKey;
import com.almworks.api.engine.*;
import com.almworks.api.gui.MainMenu;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.engine.gui.ErrorFieldController;
import com.almworks.engine.gui.SyncProblems;
import com.almworks.util.L;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.Functional;
import com.almworks.util.components.PlaceHolder;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.datetime.DateUtil;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.images.Icons;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.SetHolderUtils;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.*;
import org.almworks.util.detach.Lifecycle;
import org.jetbrains.annotations.*;

import javax.swing.*;

/**
 * @author dyoma
 */
class DummyView implements ElementViewer<ItemUiModel> {
  private Welcome myWelcome;
  private final Lifecycle myLife = new Lifecycle();
  private final IdActionProxy myUpdateAction;

  public DummyView() {
    myWelcome = new Welcome(null);
    JComponent scrollPane = myWelcome.getWelcomeComponent();
    Aqua.cleanScrollPaneBorder(scrollPane);
    Aero.cleanScrollPaneBorder(scrollPane);

    myUpdateAction = new IdActionProxy(MainMenu.Edit.DOWNLOAD);
    myUpdateAction.setOverriddenPresentation(PresentationKey.SMALL_ICON, Icons.ACTION_UPDATE_ARTIFACT);
    myUpdateAction.setOverriddenPresentation(PresentationKey.ENABLE, EnableState.ENABLED);

    myWelcome.setLinkTextActionKey(Action.NAME);
    myWelcome.setAnActions(new AnAction[] {myUpdateAction});
    myWelcome.setWelcomeVisible(true);
  }

  private void updateWelcome(Integer artifactId, ItemSyncProblem problem) {
    myWelcome.setWelcomeText(L.html("<html>" + "<p><b>Bug #" + artifactId + "</b></p>" + "<font color=\"" +
      ColorUtil.formatColor(GlobalColors.ERROR_COLOR) + "\"><b>" + getProblemMessage(problem) +
      "</b></font>" + "<p>This bug has not been downloaded from Bugzilla.</p>"));

    myUpdateAction.setOverriddenPresentation(PresentationKey.NAME, problem == null ?
      L.actionName("Download Bug #" + artifactId) : L.actionName("Try to Download Bug #" + artifactId));
    myWelcome.updateActions();
  }

  public JComponent getComponent() {
    return myWelcome.getComponent();
  }

  public PlaceHolder getToolbarPlace() {
    return null;
  }

  public PlaceHolder getBottomPlace() {
    return null;
  }

  public void showElement(ItemUiModel model) {
    myLife.cycle();
    assert model != null;
    final long item = model.getItem();
    ModelMap modelMap = model.getModelMap();
    ValueKey<Integer> valueKey = BugzillaKeys.id;
    final Integer id;
    if (valueKey == null)
      id = 0;
    else
      id = valueKey.getValue(modelMap);

    final Synchronizer synchronizer = getSynchronizer(model);
    if (synchronizer != null) {
      synchronizer.getProblems().addInitListener(myLife.lifespan(), ThreadGate.AWT, SetHolderUtils.fromChangeListener(new ChangeListener(){
        @Override
        public void onChange() {
          updateWelcome(id, Functional.first(synchronizer.getItemProblems(item)));
        }
      }));
      updateWelcome(id, Functional.first(synchronizer.getItemProblems(item)));
    }
  }

  @SuppressWarnings({"ConstantConditions"})
  @Nullable
  private static Synchronizer getSynchronizer(ItemUiModel item) {
    LoadedItemServices services = item.services();
    assert services != null;
    if (services == null)
      return null;
    Engine engine = services.getEngine();
    assert engine != null;
    if (engine == null)
      return null;
    return engine.getSynchronizer();
  }

  @Nullable
  public JComponent getToolbarEastComponent() {
    return null;
  }

  @Nullable
  public ScalarModel<? extends JComponent> getToolbarActionsHolder() {
    return null;
  }

  public void dispose() {
    myLife.cycle();
  }

  public static String getProblemMessage(ItemSyncProblem problem) {
    if (problem == null)
      return "";
    return ErrorFieldController.getMultilinePresentation(problem.getLongDescription(), 150, true) + "<b></b>" +
      "This problem has occurred " + DateUtil.toFriendlyDateTime(problem.getTimeHappened()) + ", " +
      SyncProblems.getCredentialsDescription(problem);
  }
}
