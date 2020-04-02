package com.almworks.bugzilla.provider.workflow;

import com.almworks.api.application.*;
import com.almworks.api.engine.Connection;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.bugzilla.provider.meta.ComboBoxModelKey;
import com.almworks.explorer.workflow.SetComboBoxParam;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.AComboBox;
import com.almworks.util.model.ValueModel;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

import static com.almworks.api.application.ModelOperation.SET_ITEM_KEY;

public class ResolutionEditPrimitive extends SetComboBoxParam {
  public ResolutionEditPrimitive(
    String attribute, NameMnemonic label, Collection<String> exclusions, Collection<String> inclusions)
  {
    super(attribute, SET_ITEM_KEY, label, false, exclusions, inclusions);
  }

  @NotNull
  @Override
  public Pair<JComponent, Boolean> createEditor(Lifespan lifespan, ChangeListener notifier, MetaInfo metaInfo, List<? extends ItemWrapper> items, final PropertyMap additionalProperties) throws CantPerformExceptionExplained {
    Pair<JComponent, Boolean> pair = super.createEditor(lifespan, notifier, metaInfo, items, additionalProperties);

    WorkflowTracker wt = getWorkflowTracker(items);
    if (wt == null) return pair;
    
    installPropertiesListener(lifespan, additionalProperties, wt, pair.getFirst());

    return pair;
  }

  @Nullable
  private static WorkflowTracker getWorkflowTracker(List<? extends ItemWrapper> items) {
    if (items.isEmpty()) return null;
    ItemWrapper someItem = items.iterator().next();
    Connection conn = someItem.getConnection();
    if (!(conn instanceof BugzillaConnection)) {
      assert false : conn;
      Log.warn("ResEP: unknown connection " + conn);
      return null;
    }
    BugzillaContext context = ((BugzillaConnection)conn).getContext();
    return context.getWorkflowTracker();
  }

  private void installPropertiesListener(Lifespan lifespan, final PropertyMap dependencyProperties, final WorkflowTracker wt, JComponent editor) {
    final ValueModel<Boolean> enabledModel = ValueModel.create();
    dependencyProperties.put(ENABLED_MODEL, enabledModel);
    final ComboBoxModelKey statusKey = BugzillaKeys.status;
    SelectionInListModel comboModel = getComboModel(editor);
    if (comboModel != null) {
      ChangeListener listener = new StatusListener(statusKey, dependencyProperties, wt, enabledModel, comboModel);
      listener.onChange();
      dependencyProperties.addAWTChangeListener(lifespan, listener);
    }
  }

  @Nullable
  private SelectionInListModel getComboModel(JComponent editor) {
    if (editor instanceof AComboBox) {
      return doGetComboModel((AComboBox<ItemKey>)editor);
    } else {
      assert false : editor;
      Log.warn("ResEP: unknown editor " + editor);
      return null;
    }
  }

  @Nullable
  private SelectionInListModel doGetComboModel(AComboBox<ItemKey> combo) {
    final AComboboxModel<ItemKey> cbModel = combo.getModel();
    if(cbModel instanceof SelectionInListModel) {
      return (SelectionInListModel<ItemKey>)cbModel;
    }

    if(cbModel instanceof ComboBoxModelHolder) {
      final AComboboxModel cbModel2 = ((ComboBoxModelHolder<ItemKey>)cbModel).getComboModel();
      if(cbModel2 instanceof SelectionInListModel) {
        return (SelectionInListModel<ItemKey>)cbModel2;
      }
    }

    assert false : cbModel;
    Log.warn("PDEP: unknown model " + cbModel);
    return null;
  }


  private static class StatusListener implements ChangeListener {
    private final ModelKey<ItemKey> myStatusKey;
    private final PropertyMap myDependencyProperties;
    private final WorkflowTracker myWorkflowTracker;
    private final ValueModel<Boolean> myEnabledModel;
    private final SelectionInListModel myComboModel;

    private Object myLastResolution;
    private boolean myLastEnabled;

    public StatusListener(ModelKey<ItemKey> statusKey, PropertyMap dependencyProperties, WorkflowTracker workflowTracker, ValueModel<Boolean> enabledModel, SelectionInListModel comboModel) {
      myStatusKey = statusKey;
      myDependencyProperties = dependencyProperties;
      myWorkflowTracker = workflowTracker;
      myEnabledModel = enabledModel;
      myComboModel = comboModel;
    }

    @Override
    public void onChange() {
      ItemKey status = myStatusKey.getValue(myDependencyProperties);
      if (status != null) {
        updateStatus(status, myWorkflowTracker, myEnabledModel);
      }
    }

    private void updateStatus(ItemKey status, WorkflowTracker wt, ValueModel<Boolean> enabledModel) {
      boolean enabled = wt.isOpen(status.getId()) != Boolean.TRUE;
      enabledModel.setValue(enabled);
      updateCombo(enabled);
    }

    private void updateCombo(boolean enabled) {
      if (enabled != myLastEnabled) {
        if (enabled && myLastResolution != null) {
          myComboModel.setSelectedItem(myLastResolution);
        } else if (!enabled) {
          myLastResolution = myComboModel.getSelectedItem();
          Object na = ItemKey.GET_ID.detectEqual(myComboModel.getData().toList(), BugzillaAttribute.NOT_AVAILABLE);
          myComboModel.setSelectedItem(na);
        }
        myLastEnabled = enabled;
      }
    }
  }
}
