package com.almworks.bugzilla.gui;

import com.almworks.api.application.*;
import com.almworks.api.application.field.CustomFieldsHelper;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.api.application.viewer.UIController;
import com.almworks.api.explorer.util.ConnectContext;
import com.almworks.bugzilla.provider.custom.BugzillaCustomField;
import com.almworks.bugzilla.provider.custom.VisibilityConstraint;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.ui.*;
import com.jgoodies.forms.layout.*;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifecycle;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.almworks.util.collections.Convertors.constant;
import static com.almworks.util.collections.Functional.mapValues;

class CustomFieldsController implements UIController<JPanel> {
  private static final ComponentProperty<ModelKey<?>> CONTROLLING_KEY = ComponentProperty.createProperty("CK");

  private final JPanel myFieldsPanel;
  private final ModelKey<List<ModelKey<?>>> myKey;

  private List<ModelKey<?>> myLastCustomFieldKeys;
  private List<ModelKey<?>> myLastActiveKeys;
  private Map<ModelKey<?>, Object> myLastControllerValues = Collections15.hashMap();
  private final boolean myIsSubmit;

  public CustomFieldsController(JPanel fieldsPanel, ModelKey<List<ModelKey<?>>> key, boolean isSubmit) {
    myFieldsPanel = fieldsPanel;
    myKey = key;
    assert fieldsPanel.getLayout() instanceof FormLayout : fieldsPanel.getLayout();
    myIsSubmit = isSubmit;
  }

  public void connectUI(@NotNull Lifespan lifespan, @NotNull final ModelMap model, @NotNull JPanel component) {
    myLastCustomFieldKeys = null;
    final Lifecycle reconnectLife = new Lifecycle();

    lifespan.add(reconnectLife.getDisposeDetach());
    ChangeListener listener = new ChangeListener() {
      public void onChange() {
        modelChanged(reconnectLife, model);
      }
    };
    model.addAWTChangeListener(lifespan, listener);
    modelChanged(reconnectLife, model);
  }

  private void modelChanged(Lifecycle reconnectLife, ModelMap model) {
    List<ModelKey<?>> keys = myKey.getValue(model);
    boolean changed = false;
    if (!Util.equals(keys, myLastCustomFieldKeys)) {
      // set of fields changed
      changed = true;
      myLastCustomFieldKeys = Collections15.arrayList(keys);
      Set<ModelKey<?>> fieldControllerKeys = getFieldControllerKeys(keys);
      if (!Util.equals(fieldControllerKeys, myLastControllerValues.keySet())) {
        myLastControllerValues = mapValues(fieldControllerKeys, constant(null));
      }
    }
    if (!myLastControllerValues.isEmpty()) {
      // there are some fields that influence the appearance of other fields
      for (Map.Entry<ModelKey<?>, Object> e : myLastControllerValues.entrySet()) {
        Object newValue = e.getKey().getValue(model);
        if (!Util.equals(newValue, e.getValue())) {
          changed = true;
          e.setValue(newValue);
        }
      }
    }
    if (changed) {
      List<ModelKey<?>> activeKeys = myLastCustomFieldKeys;
      if (!myLastControllerValues.isEmpty()) {
        activeKeys = filterActiveKeys(activeKeys, myLastControllerValues);
      }
      if (!Util.equals(activeKeys, myLastActiveKeys)) {
        myLastActiveKeys = Collections15.arrayList(activeKeys);
        reconnectLife.cycle();
        ConnectContext context = new ConnectContext(reconnectLife.lifespan(), model);
        rebuildCustomFieldComponents(context, myLastActiveKeys, model);
      }
    }
  }

  private static Set<ModelKey<?>> getFieldControllerKeys(List<ModelKey<?>> keys) {
    Set<ModelKey<?>> r = null;
    for (ModelKey<?> key : keys) {
      VisibilityConstraint controller = getVisibilityConstraint(key);
      if (controller == null)
        continue;
      ModelKey<?> modelKey = BugzillaCustomField.findModelKeyByControllerName(controller.getControllerFieldId(), keys);
      if (modelKey != null) {
        if (r == null)
          r = Collections15.hashSet();
        r.add(modelKey);
      }
    }
    return r == null ? Collections.<ModelKey<?>>emptySet() : r;
  }

  private static VisibilityConstraint getVisibilityConstraint(ModelKey<?> key) {
    BugzillaCustomField<?, ?> bcf = BugzillaCustomField.fromModelKey(key);
    if (bcf == null)
      return null;
    return bcf.getVisibilityConstraint();
  }

  private List<ModelKey<?>> filterActiveKeys(List<ModelKey<?>> keys, Map<ModelKey<?>, Object> controllerValues) {
    List<ModelKey<?>> r = Collections15.arrayList(keys);
    for (Iterator<ModelKey<?>> ii = r.iterator(); ii.hasNext();) {
      ModelKey<?> modelKey = ii.next();
      VisibilityConstraint controller = getVisibilityConstraint(modelKey);
      if (controller == null)
        continue;
      ModelKey<?> controllerKey = BugzillaCustomField.findModelKeyByControllerName(controller.getControllerFieldId(), keys);
      if (controllerKey == null || !controllerValues.containsKey(controllerKey))
        continue;
      if (!acceptVisibility(controller.getControllerValueIds(), controllerValues.get(controllerKey))) {
        ii.remove();
      }
    }
    return r;
  }

  private static boolean acceptVisibility(List<String> controllerValueIds, Object modelValue) {
    for (String controllerValueId : controllerValueIds) {
      if (acceptVisibility(controllerValueId, modelValue)) return true;
    }
    return false;
  }

  private static boolean acceptVisibility(String valueKey, Object modelValue) {
    if (valueKey == null)
      return true;
    if (modelValue == null)
      return false;
    if (modelValue instanceof ItemKey) {
      return valueKey.equals(((ItemKey) modelValue).getId());
    }
    if (modelValue instanceof Collection) {
      for (Object v : ((Collection) modelValue)) {
        if (v instanceof ItemKey) {
          if (valueKey.equals(((ItemKey) v).getId()))
            return true;
        }
      }
      return false;
    }
    return false;
  }

  private void rebuildCustomFieldComponents(ConnectContext context, List<ModelKey<?>> keys, ModelMap model) {
    if (keys == null)
      return;

    FormLayout layout = (FormLayout) myFieldsPanel.getLayout();

    // remove old components
    int minRow = Integer.MAX_VALUE;
    for (Component c : myFieldsPanel.getComponents()) {
      if (!(c instanceof JComponent))
        continue;
      if (CONTROLLING_KEY.getClientValue((JComponent) c) == null)
        continue;
      // a custom field component
      CellConstraints cc = layout.getConstraints(c);
      minRow = Math.min(minRow, cc.gridY);
      myFieldsPanel.remove(c);
    }

    if (minRow < Integer.MAX_VALUE) {
      int lastRow = minRow - 2;
      UIUtil.removeExcessRowsFromFormLayout(layout, lastRow);
    }

    // add new components
    DocumentFormAugmentor augmentor = new DocumentFormAugmentor();
    Point ip = new Point();
    for (ModelKey<?> key : keys) {
      BugzillaCustomField<?, ?> field = CustomFieldsHelper.getField((ModelKey<?>) key, context.getModel());
      if (field == null || myIsSubmit && !field.isAvailableOnSubmit())
        continue;
      JComponent editor = field.createValueEditor(context);
      if (editor != null) {
        JLabel label = new JLabel(key.getDisplayableName() + ":");
        label.setLabelFor(editor);
        CONTROLLING_KEY.putClientValue(label, key);
        CONTROLLING_KEY.putClientValue(editor, key);
        int height = 1;
        if (editor instanceof JScrollPane) {
          height = 4;
        }

        if (editor instanceof JTextComponent || editor instanceof JScrollPane) {
          insertFullLengthCustomField(layout, label, editor, ip, height);
        } else {
          insertHalfLengthCustomField(layout, label, editor, ip);
        }
        augmentor.augmentForm(context.getLife(), editor, true);
        DefaultUIController.connectComponent(context.getLife(), model, editor);
      }
    }

    UIUtil.setDefaultLabelAlignment(myFieldsPanel);
    Aqua.disableMnemonics(myFieldsPanel);

    myFieldsPanel.revalidate();
  }

  private void insertFullLengthCustomField(FormLayout layout, JLabel caption, JComponent editor, Point ip, int height) {
    layout.appendRow(new RowSpec("4dlu"));

    int gridY = layout.getRowCount() + 1;
    for (int i = 0; i < height; i++) {
      layout.appendRow(new RowSpec("d"));
      layout.addGroupedRow(layout.getRowCount());
    }

    CellConstraints cc =
      new CellConstraints(1, gridY, CellConstraints.FILL, height > 1 ? CellConstraints.FILL : CellConstraints.CENTER);
    myFieldsPanel.add(caption, cc);
    cc.gridHeight = height;
    cc.gridX += 2;
    cc.gridWidth = 5;
    myFieldsPanel.add(editor, cc);
    ip.x = 0;
  }

  private void insertHalfLengthCustomField(FormLayout layout, JLabel caption, JComponent editor, Point ip) {
    if (ip.x == 0) {
      // insert new rows
      layout.appendRow(new RowSpec("4dlu"));
      layout.appendRow(new RowSpec("d"));
      layout.addGroupedRow(layout.getRowCount());
    }
    CellConstraints cc =
      new CellConstraints(ip.x == 0 ? 1 : 5, layout.getRowCount(), CellConstraints.FILL, CellConstraints.CENTER);
    myFieldsPanel.add(caption, cc);
    cc.gridX += 2;
//      cc.vAlign = CellConstraints.FILL;
    myFieldsPanel.add(editor, cc);
    ip.x = 1 - ip.x;
  }
}
