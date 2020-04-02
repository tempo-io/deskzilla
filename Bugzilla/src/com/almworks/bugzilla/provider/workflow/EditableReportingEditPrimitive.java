package com.almworks.bugzilla.provider.workflow;

import com.almworks.api.application.*;
import com.almworks.explorer.workflow.SetComboBoxParam;
import com.almworks.util.Pair;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.AComboBox;
import com.almworks.util.components.recent.RecentController;
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

/**
 * Edit primitive that reports its value to the additional properties map.
 * Its purpose is to provide information for primitives that contain values depending on the value of this edit primitive.
 * The value is reported under the model key associated with this edit primitive.
 */
public class EditableReportingEditPrimitive extends SetComboBoxParam {
  public EditableReportingEditPrimitive(String attribute, NameMnemonic label,
    Collection<String> exclusions, Collection<String> inclusions)
  {
    super(attribute, SET_ITEM_KEY, label, false, exclusions, inclusions);
  }

  @NotNull
  @Override
  public Pair<JComponent, Boolean> createEditor(
    Lifespan lifespan, ChangeListener notifier, MetaInfo metaInfo, List<? extends ItemWrapper> items, final PropertyMap additionalProperties)
    throws CantPerformExceptionExplained
  {
    final Pair<JComponent, Boolean> pair = super.createEditor(lifespan, notifier, metaInfo, items, additionalProperties);

    JComponent editor = pair.getFirst();
    if(editor instanceof AComboBox) {
      installSelectionListener(lifespan, (AComboBox<ItemKey>) editor, additionalProperties, metaInfo);
    } else {
      assert false : editor;
      Log.warn("ProdEP: unknown editor " + editor);
    }

    return pair;
  }

  private void installSelectionListener(
    Lifespan lifespan, final AComboBox<ItemKey> combo, final PropertyMap dependencyProperties, MetaInfo metaInfo)
    throws CantPerformExceptionExplained
  {
    final ModelKey<ItemKey> key = (ModelKey<ItemKey>)findKey(metaInfo);
    final ChangeListener listener = new ChangeListener() {
      @Override
      public void onChange() {
        final ItemKey product = RecentController.<ItemKey>unwrap(combo.getModel().getSelectedItem());
        key.setValue(dependencyProperties, product);
      }
    };
    combo.getModifiable().addAWTChangeListener(lifespan, listener);
    listener.onChange();
  }
}
