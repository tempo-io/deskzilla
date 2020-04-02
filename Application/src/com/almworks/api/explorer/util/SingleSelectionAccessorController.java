package com.almworks.api.explorer.util;

import com.almworks.api.application.ModelKey;
import com.almworks.util.advmodel.*;
import com.almworks.util.collections.Accessor;
import com.almworks.util.collections.JointChangeListener;
import com.almworks.util.commons.Function;
import com.almworks.util.commons.Procedure;
import com.almworks.util.components.AComboBox;
import com.almworks.util.components.recent.*;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

/**
 * @author dyoma
 */
public class SingleSelectionAccessorController {
  public static <T> void connectCB(ConnectContext context, AComboBox<T> combobox, AComboboxModel<T> model, ModelKey<? extends T> key,
    boolean fixSelection, boolean fixNonModelValue, RecentController<? super T> recents) {
    connectCB(context, model, new ConnectContext.Accessor(key), fixSelection ? ListModelUtils.<T>getFirstOrNull() : null, fixNonModelValue, createSetSelected(recents, model));
    if (recents != null) AddRecentFromComboBox.install(context.getLife(), recents, combobox);
  }

  public static <T> void connectCB(final ConnectContext context, final AComboBox<T> combobox,
    final Accessor<ConnectContext, T> accessor,
    final Function<AListModel<? extends T>, T> fixSelection, boolean fixNonModel,
    @Nullable final RecentController<? super T> recents
  ) {
    final AComboboxModel<T> model = combobox.getModel();
    connectCB(context, model, accessor, fixSelection, fixNonModel, createSetSelected(recents, model));
    if (recents != null) AddRecentFromComboBox.install(context.getLife(), recents, combobox);
  }

  public static <T> Procedure<T> createSetSelected(@Nullable final RecentController<? super T> recents, final AComboboxModel<T> model) {
    return new Procedure<T>() {
      public void invoke(T arg) {
        if (recents != null) recents.setInitial(arg);
        UnwrapCombo.selectRecent(model, arg);
      }
    };
  }

  public static <T> void connectCB(final ConnectContext context, final AComboboxModel<T> model, final Accessor<ConnectContext, T> accessor,
    Function<AListModel<? extends T>, T> fixSelection, boolean fixNonModel, final Procedure<T> setSelected)
  {
    T value = accessor.getValue(context);
    if (fixSelection != null) {
      boolean fix = (value == null || fixNonModel && model.indexOf(value) < 0);
      if (fix) {
        value = fixSelection.invoke(model);
        if (value != null) accessor.setValue(context, RecentController.<T>unwrap(value));
      }
    }
    if (value != null) setSelected.invoke(value);
    JointChangeListener modelListener = new JointChangeListener() {
      protected void processChange() {
        T value = accessor.getValue(context);
        T selection = model.getSelectedItem();
        if (!Util.equals(value, selection)) {
          setSelected.invoke(value);
        }
      }
    };
    context.attachModelListener(modelListener);
    model.addSelectionChangeListener(context.getLife(), new JointChangeListener(modelListener.getUpdateFlag()) {
      protected void processChange() {
        accessor.setValue(context, RecentController.<T>unwrap(model.getSelectedItem()));
      }
    });
  }
}
