package com.almworks.api.explorer.util;

import com.almworks.api.application.ModelKey;
import com.almworks.util.collections.JointChangeListener;
import com.almworks.util.components.SelectionAccessor;

import java.util.List;

/**
 * @author dyoma
 */
public class MultiSelectionAccessorController<T> {
  private final ModelKey<List<T>> myKey;

  public MultiSelectionAccessorController(ModelKey<List<T>> key) {
    myKey = key;
  }

  public void connectUI(final ConnectContext context, final SelectionAccessor<T> selection) {
    JointChangeListener listener = new JointChangeListener() {
      protected void processChange() {
        selection.setSelected(context.getValue(myKey));
      }
    };
    context.attachModelListener(listener);
    selection.addAWTChangeListener(context.getLife(), new JointChangeListener(listener.getUpdateFlag()) {
      protected void processChange() {
        context.updateModel(myKey, selection.getSelectedItems());
      }
    });
  }
}
