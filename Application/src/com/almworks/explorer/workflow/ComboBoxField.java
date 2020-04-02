package com.almworks.explorer.workflow;

import com.almworks.api.application.*;
import com.almworks.api.dynaforms.AbstractWorkflowField;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.AComboBox;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.*;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.ComponentProperty;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

public class ComboBoxField implements AbstractWorkflowField<AComboBox<ItemKey>> {
  private static final ComponentProperty<ItemKey> LAST_SELECTED = ComponentProperty.createProperty("lastSelected");
  private static final ItemKey ourDontChange =
    new ItemKeyStub("___dontchange__", WorkflowUtil.DONT_CHANGE, ItemOrder.NO_ORDER);

  private final ModelKey<ItemKey> myModelKey;
  private final ResolvedItem myNovalue;
  private final BaseEnumConstraintDescriptor myEnumDescriptor;
  private final DBAttribute<Long> myAttribute;
  private final ScalarModel<ItemHypercube> mySourceCube;
  @Nullable
  private final ScalarModelSetter<ItemHypercube> myTargetCube;
  private final boolean myAllowDontChange;
  @Nullable
  private final Collection<? extends ItemKey> myExcluded;
  private final Collection<? extends ItemKey> myIncluded;
  private final Condition<ItemKey> myFilter;

  public ComboBoxField(ModelKey<ItemKey> modelKey, ResolvedItem novalue,
    BaseEnumConstraintDescriptor enumDescriptor, DBAttribute<Long> attribute, ScalarModel<ItemHypercube> sourceCube,
    @Nullable ScalarModelSetter<ItemHypercube> targetCube, boolean allowDontChangeValue,
    @Nullable Condition<ItemKey> filter, Collection<? extends ItemKey> included, Collection<? extends ItemKey> excluded)
  {
    // here fixed exclude list

    myModelKey = modelKey;
    myNovalue = novalue;
    myEnumDescriptor = enumDescriptor;
    myAttribute = attribute;
    mySourceCube = sourceCube;
    myTargetCube = targetCube;
    myAllowDontChange = allowDontChangeValue;
    myFilter = filter;
    myExcluded = excluded;
    myIncluded = included;
  }

  public void setValue(ItemUiModel model, AComboBox<ItemKey> component) throws CantPerformExceptionExplained {
    Object selectedItem = component.getModel().getSelectedItem();
    // catching CCE
    if (selectedItem != null && !(selectedItem instanceof ItemKey)) {
      Log.warn("bad selectedItem: [" + selectedItem + "][" + component + "]");
      selectedItem = ourDontChange;
    }
    setValue(model, myModelKey, (ItemKey) selectedItem);
  }

  public static void setValue(ItemUiModel model, ModelKey<ItemKey> key, ItemKey item) {
    if (item != ourDontChange && item != null) {
      ModelMap map = model.getModelMap();
      PropertyMap props = new PropertyMap();
      key.takeSnapshot(props, map);
      key.setValue(props, item);
      key.copyValue(map, props);
    }
  }

  public String getSaveProblem(AComboBox<ItemKey> component, MetaInfo metaInfo) {
    ItemKey item = component.getModel().getSelectedItem();
    return WorkflowUtil.getEnumSaveProblem(myModelKey, item, myIncluded, myExcluded);
  }

  @Nullable
  public Pair<AComboBox<ItemKey>, Boolean> createEditor(Lifespan lifespan, ChangeListener changeNotifier,
      MetaInfo metaInfo, List<? extends ItemWrapper> items, PropertyMap additionalProperties)
  {
    AListModel<ItemKey> variants = WorkflowUtil.createEnumModel(lifespan, mySourceCube, myEnumDescriptor, myFilter);
    return createComboEditor(lifespan, changeNotifier, items, variants, myExcluded, myIncluded, myTargetCube,
      mySourceCube, myAllowDontChange, myNovalue, myModelKey, myEnumDescriptor, myAttribute);
  }

  public static Pair<AComboBox<ItemKey>, Boolean> createComboEditor(Lifespan lifespan, ChangeListener changeNotifier, List<? extends ItemWrapper> items,
    AListModel<ItemKey> variants, Collection<? extends ItemKey> excluded, Collection<? extends ItemKey> included,
    ScalarModelSetter<ItemHypercube> targetCube, ScalarModel<ItemHypercube> sourceCube, boolean allowDontChange,
    ResolvedItem novalue, ModelKey<ItemKey> modelKey, BaseEnumConstraintDescriptor enumDescriptor,
    DBAttribute<Long> attribute) {
    final AComboBox<ItemKey> combo = new AComboBox<ItemKey>();

    if (excluded != null) {
      variants = FilteringListDecorator.exclude(lifespan, variants, excluded);
    }
    if (included != null) {
      variants = FilteringListDecorator.include(lifespan, variants, included);
    }

    SelectionInListModel<ItemKey> model = SelectionInListModel.create(lifespan, variants, null);

    //moved. bug: selection was changed before model setuped - first selection change handle missed
    combo.setModel(model);

    if (targetCube != null) {
      MyCubeAndSelectionListener listener = new MyCubeAndSelectionListener(combo, enumDescriptor, attribute,
        sourceCube, targetCube);
      model.addSelectionListener(lifespan, listener);
      sourceCube.getEventSource().addListener(lifespan, ThreadGate.AWT, listener);
    }

    ItemKey select = ourDontChange;
    if (model.getSize() > 0) {
      if (items.size() == 1) {
        ItemKey value = items.get(0).getModelKeyValue(modelKey);
        if (value != null) {
          if (!allowDontChange && value.equals(novalue)) {
            select = getFirstMeaningfulValue(model, novalue);
          } else {
            select = value;
          }
        }
      } else {
        ItemKey same = null;
        boolean hasSame = true;

        for (ItemWrapper item : items) {
          ItemKey value = item.getModelKeyValue(modelKey);
          if (same == null) {
            same = value;
          } else {
            if (!same.equals(value)) {
              hasSame = false;
              break;
            }
          }
        }

        if (hasSame) {
          select = same;
        } else if (allowDontChange) {
          select = ourDontChange;
        } else {
          select = getFirstMeaningfulValue(model, novalue);
        }
      }
      model.setSelectedItem(select);
    }
    model.addSelectionChangeListener(lifespan, changeNotifier);
    model.addChangeListener(lifespan, changeNotifier);
    combo.setCanvasRenderer(Renderers.defaultCanvasRenderer());
    combo.setMinimumSize(new Dimension(0, 0));
    Boolean b = allowDontChange ? (select != ourDontChange) : null;
    if (included != null || excluded != null)
      b = null;
    return Pair.create(combo, b);
  }

  @Nullable
  public static ItemKey getFirstMeaningfulValue(AListModel<ItemKey> model, ResolvedItem ignore) {
    int size = model.getSize();
    if (size == 0)
      return null;
    ItemKey select = model.getAt(0);
    if (select.equals(ignore) && size > 1) {
      select = model.getAt(1);
    }
    return select;
  }

  public static boolean isDontChange(ItemKey key) {
    return ourDontChange == key;
  }

  public NameMnemonic getLabel(MetaInfo metaInfo) {
    return NameMnemonic.parseString(myModelKey.getDisplayableName() + ":");
  }

  public boolean isInlineLabel() {
    return true;
  }

  public double getEditorWeightY() {
    return 0;
  }

  public JComponent getInitialFocusOwner(AComboBox<ItemKey> component) {
    return component.getCombobox();
  }

  public boolean isConsiderablyModified(AComboBox<ItemKey> component) {
    return false;
  }

  @Override
  public void addAffectedFields(List<DBAttribute> fieldList) {
    fieldList.add(myAttribute);
  }

  public void enablePrimitive(AComboBox<ItemKey> component, boolean enabled) {
    enableComponent(component, enabled, component.getModel(), myNovalue);
  }

  public static void enableComponent(JComponent component, boolean enabled, AComboboxModel<ItemKey> model,
    ResolvedItem novalue)
  {
    final ItemKey lastSelected = LAST_SELECTED.getClientValue(component);
    boolean wasEnabled = component.isEnabled();
    if (wasEnabled != enabled) {
      if (wasEnabled) {
        LAST_SELECTED.putClientValue(component, model.getSelectedItem());
      }
      component.setEnabled(enabled);
      if (!enabled) {
        model.setSelectedItem(ourDontChange);
      } else {
        if (lastSelected != null && lastSelected != ourDontChange) {
          model.setSelectedItem(lastSelected);
        } else {
          model.setSelectedItem(getFirstMeaningfulValue(model, novalue));
        }
      }
    }
  }

  private static class MyCubeAndSelectionListener extends SelectionListener.SelectionOnlyAdapter
    implements ScalarModel.Consumer<ItemHypercube>
  {
    private final AComboBox<ItemKey> myCombo;
    private final BaseEnumConstraintDescriptor myEnumDescriptor;
    private final DBAttribute<Long> myAttribute;
    private final ScalarModel<ItemHypercube> mySourceCube;
    @Nullable
    private final ScalarModelSetter<ItemHypercube> myTargetCube;

    public MyCubeAndSelectionListener(AComboBox<ItemKey> combo, BaseEnumConstraintDescriptor enumDescriptor,
      DBAttribute<Long> attribute, ScalarModel<ItemHypercube> sourceCube,
      ScalarModelSetter<ItemHypercube> targetCube) {
      myCombo = combo;
      myEnumDescriptor = enumDescriptor;
      myAttribute = attribute;
      mySourceCube = sourceCube;
      myTargetCube = targetCube;
    }

    public void onSelectionChanged() {
      updateTargetCube(mySourceCube.getValue(), myCombo);
    }

    public void onContentKnown(ScalarModelEvent<ItemHypercube> event) {
    }

    public void onScalarChanged(ScalarModelEvent<ItemHypercube> event) {
      onSelectionChanged();
    }

    private void updateTargetCube(ItemHypercube cube, AComboBox<ItemKey> combo) {
      if (myTargetCube != null) {
        ItemKey selectedItem = combo.getModel().getSelectedItem();
        if (selectedItem != null && selectedItem != ourDontChange) {
          List<ResolvedItem> items = myEnumDescriptor.resolveKey(selectedItem, cube);
          if (items != null && items.size() > 0) {
            ItemHypercubeImpl copy = (ItemHypercubeImpl) cube.copy();
            for (ResolvedItem item : items) {
              copy.addValue(myAttribute, item.getResolvedItem(), true);
            }
            myTargetCube.setValue(copy);
          }
        }
      }
    }
  }
}
