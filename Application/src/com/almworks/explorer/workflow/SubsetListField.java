package com.almworks.explorer.workflow;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.api.dynaforms.AbstractWorkflowField;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.ListModelUtils;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.CompactSubsetEditor;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.GlobalColors;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public class SubsetListField implements AbstractWorkflowField<CompactSubsetEditor<ItemKey>> {
  private final ModelKey<List<ItemKey>> myModelKey;
  private final BaseEnumConstraintDescriptor myEnumDescriptor;
  private final DBAttribute<List<Long>> myAttribute;
  private final ModelKey<ItemKey> myParentModelKey;
  private final DBAttribute<Long> myParentAttribute;
  private final ScalarModel<ItemHypercube> myCubeModel;
  private final Set<Long> myParents;

  private final CompactSubsetEditor<ItemKey> myEditor = new CompactSubsetEditor<ItemKey>();

  private final Condition<ItemKey> myFilter;
  private final Comparator<ItemKey> myOrder;
  private final List<? extends ItemKey> myIncluded;
  private final List<? extends ItemKey> myExcluded;

  private List<ItemKey> myLastSetValues;

  public SubsetListField(ModelKey<List<ItemKey>> modelKey, BaseEnumConstraintDescriptor enumDescriptor,
    DBAttribute<List<Long>> attribute, ModelKey<ItemKey> parentModelKey, DBAttribute<Long> parentAttribute,
    ScalarModel<ItemHypercube> cube, Set<Long> parents, Condition<ItemKey> filter,
    Comparator<ItemKey> order, List<? extends ItemKey> included, List<? extends ItemKey> excluded)
  {
    myModelKey = modelKey;
    myEnumDescriptor = enumDescriptor;
    myAttribute = attribute;
    myParentModelKey = parentModelKey;
    myParentAttribute = parentAttribute;
    myCubeModel = cube;
    myParents = parents;
    myFilter = filter;
    myOrder = order;
    myIncluded = included;
    myExcluded = excluded;

    myEditor.setUnknownSelectionItemColor(GlobalColors.ERROR_COLOR);
    myEditor.setCanvasRenderer(DefaultUIController.ITEM_KEY_RENDERER);
    myEditor.setVisibleRowCount(3);
    myEditor.setPrototypeValue(new ItemKeyStub("-- a prototype -- of compo"));
    myEditor.setMinimumSize(new Dimension(0, 0));
    myEditor.setNothingSelectedItem(new ItemKeyStub("None"));
  }


  public boolean init(Lifespan lifespan, List<? extends ItemWrapper> items) {
    List<ItemKey> sameValues = null;
    boolean haveSameValues = true;
    for (ItemWrapper item : items) {
      if (haveSameValues) {
        List<ItemKey> values = item.getModelKeyValue(myModelKey);
        if (sameValues == null) {
          sameValues = values;
        } else {
          if (!sameValues.equals(values)) {
            haveSameValues = false;
          }
        }
      }
    }
    AListModel<ItemKey> variants = WorkflowUtil.createEnumModel(lifespan, myCubeModel, myEnumDescriptor, null);
    variants = ListModelUtils.addFilterAndSorting(lifespan, variants, myFilter, myOrder);
    myEditor.setFullModel(variants);
    myCubeModel.getEventSource().addAWTListener(lifespan, new ScalarModel.Adapter<ItemHypercube>() {
      public void onScalarChanged(ScalarModelEvent<ItemHypercube> event) {
        myEditor.repaint();
      }
    });

    boolean same = myParents.size() == 1 && haveSameValues && sameValues != null;
    if (same) {
      myLastSetValues = sameValues;
      myEditor.setSelected(sameValues);
    }
    enablePrimitive(myEditor, same);
    return same;
  }

  public void applyTo(ItemUiModel model) {
    if (myEditor.isEnabled()) {
      ModelMap map = model.getModelMap();

      ItemKey projectKey = myParentModelKey.getValue(map);
      List<ItemKey> items = myEditor.getSubsetAccessor().getSelectedItems();

      // verify
      MetaInfo meta = model.getMetaInfo();
      if (meta != null) {
        PropertyMap newProps = model.takeSnapshot();
        myModelKey.setValue(newProps, items);
        String error = meta.getVerifierManager().callContextFunc(model, myModelKey, model.getLastDBValues(), newProps);
        if (error != null) {
          return;
        }
      }

      ItemHypercubeImpl cube = (ItemHypercubeImpl) myCubeModel.getValue();
      if (!cube.containsAxis(myParentAttribute)) {
        cube = cube.copy();
        cube.addValue(myParentAttribute, projectKey.getResolvedItem(), true);
      }

      // remove items that do not belong to the artifact's project
      for (Iterator<ItemKey> ii = items.iterator(); ii.hasNext();) {
        ItemKey artifactKey = ii.next();
        List<ResolvedItem> list = myEnumDescriptor.resolveKey(artifactKey, cube);
        if (list.size() != 1) {
          ii.remove();
        }
      }

      PropertyMap props = new PropertyMap();
      myModelKey.takeSnapshot(props, map);
      List<ItemKey> oldValue = myModelKey.getValue(props);
      if (oldValue == null)
        oldValue = Collections15.emptyList();
      List<ItemKey> newValue;
      newValue = items;
      myModelKey.setValue(props, newValue);
      myModelKey.copyValue(map, props);
    }
  }

  public void setValue(ItemUiModel model, CompactSubsetEditor<ItemKey> component)
    throws CantPerformExceptionExplained
  {
    assert component == myEditor : this + " " + component;
    applyTo(model);
  }

  public String getSaveProblem(CompactSubsetEditor<ItemKey> component, MetaInfo metaInfo) {
    assert component == myEditor : this + " " + component;
    if (myIncluded == null && myExcluded == null)
      return null;
    List<?> items = myEditor.getSubsetAccessor().getSelectedItems();
    return WorkflowUtil.getEnumSetSaveProblem(myModelKey, items, myIncluded, myExcluded);
  }

  public Pair<CompactSubsetEditor<ItemKey>, Boolean> createEditor(Lifespan lifespan, ChangeListener changeNotifier,
      MetaInfo metaInfo, List<? extends ItemWrapper> items, PropertyMap additionalProperties)
  {
    Boolean enabled = init(lifespan, items);
    myEditor.getSubsetModel().addChangeListener(lifespan, changeNotifier);
    if (myIncluded != null || myExcluded != null)
      enabled = null;
    return Pair.create(myEditor, enabled);
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

  public JComponent getInitialFocusOwner(CompactSubsetEditor<ItemKey> component) {
    return null;
  }

  public boolean isConsiderablyModified(CompactSubsetEditor<ItemKey> component) {
    assert component == myEditor : component + " " + this;
    return false;
  }

  @Override
  public void addAffectedFields(List<DBAttribute> fieldList) {
    fieldList.add(myAttribute);
  }

  public void enablePrimitive(CompactSubsetEditor<ItemKey> component, boolean enabled) {
    boolean wasEnabled = component.isEnabled();
    if (wasEnabled != enabled) {
      if (wasEnabled) {
        myLastSetValues = myEditor.getSubsetAccessor().getSelectedItems();
      }
      component.setEnabled(enabled);
      List<ItemKey> selected;
      if (enabled && myLastSetValues != null) {
        selected = myLastSetValues;
      } else {
        selected = Collections15.<ItemKey>emptyList();
      }
      myEditor.setSelected(selected);
    }
  }
}
