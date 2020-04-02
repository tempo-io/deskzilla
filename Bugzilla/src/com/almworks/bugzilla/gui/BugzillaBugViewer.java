package com.almworks.bugzilla.gui;

import com.almworks.api.application.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.PlaceHolder;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.ui.ElementViewer;
import org.almworks.util.detach.Lifecycle;

import javax.swing.*;

/**
 * @author dyoma
 */
public class BugzillaBugViewer implements ElementViewer<ItemUiModel> {
  private final ElementViewer<ItemUiModel> myGeneralViewer;
  private final PlaceHolder myViewerPlace = new PlaceHolder();
  private final ModelKey<Boolean> myDummyFlag;
  private final DummyView myDummyView = new DummyView();
  private final PlaceHolder myToolbarComponent = new PlaceHolder();
  private final Lifecycle myDummyLife = new Lifecycle();
  private ItemUiModel myCurrentElement = null;
  private ScalarModel<? extends JComponent> myToolbarActionsHolder;

  public BugzillaBugViewer(ModelKey<Boolean> dummyFlag, ElementViewer<ItemUiModel> generalViewer) {
    myGeneralViewer = generalViewer;
    myDummyFlag = dummyFlag;
  }

  public void showElement(ItemUiModel item) {
    if (item == myCurrentElement)
      return;
    myDummyLife.cycle();
    final ModelMap modelsMap = item.getModelMap();
    boolean isDummy = myDummyFlag.getValue(modelsMap).booleanValue();
    myCurrentElement = item;
    if (!isDummy) {
      showNotDummyBug();
      return;
    }
    myToolbarActionsHolder = null;
    myViewerPlace.show(myDummyView);
    myDummyView.showElement(myCurrentElement);
    myToolbarComponent.clear();
    modelsMap.addAWTChangeListener(myDummyLife.lifespan(), new ChangeListener() {
      public void onChange() {
        boolean isDummy = myDummyFlag.getValue(modelsMap).booleanValue();
        if (isDummy)
          return;
        myDummyLife.cycle();
        showNotDummyBug();
      }
    });
  }

  private void showNotDummyBug() {
    myViewerPlace.show(myGeneralViewer);
    myToolbarComponent.show(myGeneralViewer.getToolbarEastComponent());
    myGeneralViewer.showElement(myCurrentElement);
    myToolbarActionsHolder = myGeneralViewer.getToolbarActionsHolder();
  }

  public JComponent getToolbarEastComponent() {
    return myToolbarComponent;
  }

  public ScalarModel<? extends JComponent> getToolbarActionsHolder() {
    return myToolbarActionsHolder;
  }

  public JComponent getComponent() {
    return myViewerPlace;
  }

  public PlaceHolder getToolbarPlace() {
    if(myGeneralViewer != null) {
      return myGeneralViewer.getToolbarPlace();
    }
    return null;
  }

  public PlaceHolder getBottomPlace() {
    return myGeneralViewer != null ? myGeneralViewer.getBottomPlace() : null;
  }

  public void dispose() {
    myDummyLife.dispose();
    myViewerPlace.dispose();
    myDummyView.dispose();
    myToolbarComponent.dispose();
    myGeneralViewer.dispose();
  }
}
