package com.almworks.explorer.workflow;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.api.explorer.gui.AbstractComboBoxModelKey;
import com.almworks.api.explorer.gui.ItemModelKey;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.advmodel.SelectionInListModel;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.AComboBox;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.completion.CompletingComboBox;
import com.almworks.util.components.completion.CompletingComboBoxController;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.*;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;

import static org.almworks.util.Collections15.arrayList;
import static org.almworks.util.Collections15.hashSet;

public class SetComboBoxParam extends SetParam<ItemKey> {
  private final boolean myEditable;
  private final Collection<String> myExclusions;
  private final Collection<String> myInclusions;

  private CanvasRenderer<ItemKey> myRenderer;
  private boolean myCaseSensitive = true;

  public SetComboBoxParam(String attribute, TypedKey<ItemKey> operation, NameMnemonic label, boolean editable) {
    this(attribute, operation, label, editable,
      Collections15.<String>emptyCollection(), Collections15.<String>emptyCollection());
  }

  public SetComboBoxParam(String attribute, TypedKey<ItemKey> operation, NameMnemonic label, boolean editable,
    Collection<String> exclusions, Collection<String> inclusions)
  {
    super(attribute, operation, label);
    myEditable = editable;
    myExclusions = exclusions;
    myInclusions = inclusions;
  }

  @NotNull
  public Pair<JComponent, Boolean> createEditor(Lifespan lifespan, final ChangeListener notifier, MetaInfo metaInfo,
      List<? extends ItemWrapper> items, PropertyMap additionalProperties) throws CantPerformExceptionExplained
  {
    // Return CompletingComboBox if editable, AComboBox otherwise.
    // Uneditable CompletingComboBoxes don't look good on Mac.
    if (myEditable) {
      final CompletingComboBox<ItemKey> comboBox = createEditableCombo(metaInfo, items);
      comboBox.getController().getModel().addSelectionChangeListener(lifespan, notifier);
      return Pair.create((JComponent)comboBox, null);
    } else {
      final AComboBox<ItemKey> comboBox = createUneditableCombo(metaInfo, items);
      comboBox.getModel().addSelectionChangeListener(lifespan, notifier);
      return Pair.create((JComponent)comboBox, null);
    }
  }

  private CompletingComboBox<ItemKey> createEditableCombo(MetaInfo metaInfo, List<? extends ItemWrapper> items) throws
    CantPerformExceptionExplained
  {
    final Pair<SelectionInListModel<ItemKey>, AbstractComboBoxModelKey> pair = createModel(metaInfo, items);

    final CompletingComboBoxController<ItemKey> controller = new CompletingComboBoxController<ItemKey>();
    final CompletingComboBox<ItemKey> comboBox = controller.getComponent();
    controller.setModel(pair.getFirst());
    controller.setCanvasRenderer(Util.NN(myRenderer, DefaultUIController.ITEM_KEY_RENDERER));
    controller.setCasesensitive(myCaseSensitive);

    final AbstractComboBoxModelKey key = pair.getSecond();
    if (key instanceof ItemModelKey<?>) {
      controller.setConvertors(ItemKey.DISPLAY_NAME, ((ItemModelKey<?>) key).getResolver(), ItemKey.DISPLAY_NAME_EQUALITY);
      comboBox.setEditable(true);
    } else {
      assert false : key;
    }

    return comboBox;
  }

  private AComboBox<ItemKey> createUneditableCombo(MetaInfo metaInfo, List<? extends ItemWrapper> items) throws
    CantPerformExceptionExplained
  {
    final AComboBox<ItemKey> comboBox = new AComboBox<ItemKey>();
    comboBox.setCanvasRenderer(Util.NN(myRenderer, DefaultUIController.ITEM_KEY_RENDERER));
    comboBox.setModel(createModel(metaInfo, items).getFirst());
    return comboBox;
  }

  private Pair<SelectionInListModel<ItemKey>, AbstractComboBoxModelKey> createModel(
    MetaInfo metaInfo, List<? extends ItemWrapper> items) throws CantPerformExceptionExplained
  {
    AbstractComboBoxModelKey key = (AbstractComboBoxModelKey) findKey(metaInfo);
    OrderListModel<ItemKey> variants = OrderListModel.create();

    PropertyMap lastValues = null;
    boolean commonSelectedValue = true;

    Set<String> variantIdsSet = hashSet();
    List<ItemKey> variantsList = arrayList();
    for (ItemWrapper wrapper : items) {
      PropertyMap values = wrapper.getLastDBValues();

      if (commonSelectedValue) {
        if (lastValues == null) {
          lastValues = values;
        } else {
          if (!key.isEqualValue(lastValues, values)) {
            lastValues = null;
            commonSelectedValue = false;
          }
        }
      }

      for (ResolvedItem ri : key.getApplicableVariantsList(values)) {
        String id = ri.getId();
        if (!myExclusions.contains(id) && (myInclusions.isEmpty() || myInclusions.contains(id))) {
          boolean added = variantIdsSet.add(id);
          if (added) {
            String displayName = ri.getDisplayName();
            variantsList.add(new ItemKeyStub(id, displayName, ItemOrder.byString(displayName)));
          }
        }
      }
    }
    
    Collections.sort(variantsList);
    variants.addAll(variantsList);
    
    ItemKey initial = null;
    if(commonSelectedValue && lastValues != null) {
      initial = key.getValue(lastValues);
    }

    if(!myInclusions.isEmpty()
      && (initial == null || !variants.contains(initial))
      && variants.getSize() > 0)
    {
      initial = variants.getAt(0);
    }

    return Pair.create(SelectionInListModel.createForever(variants, initial), key);
  }

  public boolean isInlineLabel() {
    return true;
  }

  @Nullable
  protected ItemKey getValue(JComponent component) {
    if(component instanceof CompletingComboBox) {
      return ((CompletingComboBox<ItemKey>) component).getController().getModel().getSelectedItem();
    } else if(component instanceof AComboBox) {
      return ((AComboBox<ItemKey>) component).getModel().getSelectedItem();
    }
    assert false : component;
    return null;
  }

  public void enablePrimitive(JComponent component, boolean enabled) {
    component.setEnabled(enabled);
  }

  public void setCanvasRenderer(CanvasRenderer<ItemKey> renderer) {
    myRenderer = renderer;
  }

  public void setCaseSensitive(boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
  }

  @Override
  public boolean isApplicable(MetaInfo metaInfo, List<ItemWrapper> items) {
    if(myInclusions.isEmpty()) {
      return true;
    }
    try {
      final Pair<SelectionInListModel<ItemKey>, AbstractComboBoxModelKey> pair = createModel(metaInfo, items);
      return pair.getFirst().getSize() > 0;
    } catch (CantPerformExceptionExplained cantPerformExceptionExplained) {
      return false;
    }
  }
}

