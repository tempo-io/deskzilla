package com.almworks.bugzilla.gui;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.util.advmodel.AComboboxModel;
import com.almworks.util.advmodel.SelectionInListModel;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.AComboBox;
import com.almworks.util.components.recent.RecentController;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;

import static java.lang.Boolean.TRUE;

/**
 * @author Vasya
 */
public class ResolutionController extends DefaultUIController<AComboBox> {
  private final JComponent myDuplicateOfEditor;
  private final JLabel myLabel;

  public ResolutionController(JComponent duplicateOfEditor, JLabel label) {
    myDuplicateOfEditor = duplicateOfEditor;
    myLabel = label;
  }

  protected void connectUI(Lifespan lifespan, ModelMap model, final AComboBox component, ModelKey key) {
    assert key == BugzillaKeys.resolution;
    AComboboxModel cbModel = (AComboboxModel) key.getModel(lifespan, model, AComboboxModel.class);
    SelectionInListModel silm = null;
    if (cbModel instanceof SelectionInListModel) {
      silm = (SelectionInListModel) cbModel;
    } else {
      assert false : cbModel;
      Log.warn("ResCon: unknown model");
    }
    BugzillaFormUtils.setupArtifactComboBox(lifespan, component, cbModel, model, "resolution");
    StatusListener statusListener = new StatusListener(model, component, silm);
    model.addAWTChangeListener(lifespan, statusListener);
    statusListener.onChange();
    component.getModifiable().addAWTChangeListener(lifespan, new ChangeListener() {
      @Override
      public void onChange() {
        updateDuplicateOfPanel(component);
      }
    });
    updateDuplicateOfPanel(component);
  }

  private void updateDuplicateOfPanel(AComboBox resolutionCombo) {
    Object item = resolutionCombo.getModel().getSelectedItem();
    myDuplicateOfEditor.setVisible(isDuplicate(item));
  }

  // todo :kludge: hard-coded "DUPLICATE" status.
  private boolean isDuplicate(Object item) {
    Object unwrapped = RecentController.unwrap(item);
    if (unwrapped instanceof ItemKey) {
      String resolution = ((ItemKey) unwrapped).getDisplayName();
      return resolution.trim().equalsIgnoreCase("DUPLICATE");
    }
    return false;
  }


  private class StatusListener implements ChangeListener {
    private final ModelMap myModel;
    private final AComboBox myComponent;
    private final SelectionInListModel myComboModel;
    private ItemKey myLastStatus;
    private Object myLastResolution;
    private boolean myLastOpen;

    public StatusListener(ModelMap model, AComboBox component, SelectionInListModel comboModel) {
      myModel = model;
      myComponent = component;
      myComboModel = comboModel;
    }

    @Override
    public void onChange() {
      ItemKey status = BugzillaKeys.status.getValue(myModel);
      if (!Util.equals(status, myLastStatus)) {
        myLastStatus = status;
        Boolean open = isOpen(status);
        updateStatus(open);
        updateModel(open);
        updateDuplicateOfPanel(myComponent);
      }
    }

    private void updateModel(Boolean open) {
      if (open == TRUE && !myLastOpen) {
        myLastResolution = myComboModel.getSelectedItem();
        Object na = ItemKey.GET_ID.detectEqual(myComboModel.getData().toList(), BugzillaAttribute.NOT_AVAILABLE);
        myComboModel.setSelectedItem(na);
      } else if (open != TRUE && myLastResolution != null && myLastOpen) {
        myComboModel.setSelectedItem(myLastResolution);
      }
      myLastOpen = (open == TRUE);
    }

    private void updateStatus(Boolean isOpen) {
      boolean enabled = isOpen != Boolean.TRUE;
      myComponent.setEnabled(enabled);
      myDuplicateOfEditor.setEnabled(enabled);
      myLabel.setEnabled(enabled);
    }

    private Boolean isOpen(ItemKey status) {
      BugzillaContext ctx = BugzillaUtil.getContext(myModel);
      if (ctx != null) {
        return ctx.getWorkflowTracker().isOpen(status.getId());
      }
      assert false;
      Log.warn("ResCon: no ctx " + myModel);
      return false;
    }
  }
}
