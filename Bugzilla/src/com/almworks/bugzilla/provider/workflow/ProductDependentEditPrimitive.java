package com.almworks.bugzilla.provider.workflow;

import com.almworks.api.application.*;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.bugzilla.provider.meta.ComboBoxModelKey;
import com.almworks.explorer.workflow.SetComboBoxParam;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.AComboBox;
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
import static com.almworks.explorer.qbuilder.filter.ItemKeyModelCollector.DERESOLVER;
import static com.almworks.util.collections.Containers.toUniqueSortedList;

public class ProductDependentEditPrimitive extends SetComboBoxParam {
  public ProductDependentEditPrimitive(
    String attribute, NameMnemonic label, Collection<String> exclusions, Collection<String> inclusions)
  {
    super(attribute, SET_ITEM_KEY, label, false, exclusions, inclusions);
  }

  @NotNull
  @Override
  public Pair<JComponent, Boolean> createEditor(
    final Lifespan lifespan, ChangeListener notifier, final MetaInfo metaInfo,
    List<? extends ItemWrapper> items, final PropertyMap additionalProperties)
    throws CantPerformExceptionExplained
  {
    Pair<JComponent, Boolean> pair = super.createEditor(lifespan, notifier, metaInfo, items, additionalProperties);
    if (items.isEmpty()) return pair;
    JComponent editor = pair.getFirst();
    if(editor instanceof AComboBox) {
      AComboBox<ItemKey> combo = (AComboBox<ItemKey>) editor;
      PropertyMap anyBug = items.get(0).getLastDBValues();
      installMapListener(lifespan, additionalProperties, combo, anyBug, metaInfo);
    } else {
      assert false : editor;
      Log.warn("PDEP: unknown editor " + editor);
    }

    return pair;
  }

  private void installMapListener(
    final Lifespan lifespan, final PropertyMap dependencyProperties, AComboBox<ItemKey> combo,
    PropertyMap anyBug, MetaInfo metaInfo) throws CantPerformExceptionExplained
  {
    final PropertyMap map = new PropertyMap(anyBug);
    final ComboBoxModelKey dependentKey = (ComboBoxModelKey) findKey(metaInfo);
    final SelectionInListModel<ItemKey> silModel = extractSelectionModel(combo);
    if (silModel == null) return;

    ChangeListener listener = new ChangeListener() {
      @Override
      public void onChange() {
        final ItemKey product = BugzillaKeys.product.getValue(dependencyProperties);
        if (product != null) {
          BugzillaKeys.product.setValue(map, product);
          List<ItemKey> variants = toUniqueSortedList(DERESOLVER.collectList(dependentKey.getApplicableVariantsList(map)));
          silModel.setData(lifespan, FixedListModel.create(variants));
          adjustSelectedValue(silModel);
        }
      }
    };

    dependencyProperties.addAWTChangeListener(lifespan, listener);
    listener.onChange();
  }

  private void adjustSelectedValue(SelectionInListModel<ItemKey> silModel) {
    ItemKey value = silModel.getSelectedItem();
    if (silModel.indexOf(value) == -1 && silModel.getSize() > 0) {
      silModel.setSelectedItem(silModel.getAt(0));
    }
  }

  @Nullable
  private SelectionInListModel<ItemKey> extractSelectionModel(AComboBox<ItemKey> combo) {
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
}
