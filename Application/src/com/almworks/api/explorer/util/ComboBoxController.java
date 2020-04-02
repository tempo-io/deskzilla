package com.almworks.api.explorer.util;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.api.application.viewer.UIController;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.SelectionInListModel;
import com.almworks.util.collections.Convertor;
import com.almworks.util.components.AComboBox;
import com.almworks.util.components.recent.RecentController;
import com.almworks.util.config.Configuration;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

/**
 * @author dyoma
 */
public class ComboBoxController<T> implements UIController<AComboBox<T>>, VariantsAcceptor<T> {
  private final VariantsConfigurator<T> myVariants;
  private final RecentController myRecents = new RecentController();
  private final ModelKey<T> myKey;
  private final boolean mySelectFirstIfNoValue;
  private final boolean myFixNonModelValue;

  public ComboBoxController(ModelKey<T> key, boolean selectFirstIfNoValue, boolean fixNonModelValue,
    VariantsConfigurator<T> configurator)
  {
    myKey = key;
    mySelectFirstIfNoValue = selectFirstIfNoValue;
    myFixNonModelValue = fixNonModelValue;
    myRecents.setRenderer(DefaultUIController.ITEM_KEY_RENDERER);
    myVariants = configurator;
    myRecents.setIdentityConvertor(myVariants.getIdentityConvertor());
    myRecents.setWrapRecents(true);
  }

  public void connectUI(@NotNull Lifespan lifespan, @NotNull final ModelMap model, @NotNull AComboBox<T> cb) {
    ConnectContext context = new ConnectContext(lifespan, model);
    myVariants.configure(context, this);
    SelectionInListModel<T> cbModel = myRecents.setupAComboBox(cb, lifespan);
    SingleSelectionAccessorController.connectCB(context, cb, cbModel, myKey, mySelectFirstIfNoValue, myFixNonModelValue, myRecents);
    lifespan.add(myRecents.createDetach());
  }

  public void accept(AListModel<T> variants, @Nullable Configuration recentConfig) {
    myRecents.setup(variants, recentConfig);
  }

  public static ComboBoxController<ItemKey> install(AComboBox<ItemKey> cb, ModelKey<ItemKey> key,
    BaseEnumConstraintDescriptor descriptor, Convertor<ModelMap, ItemHypercube> cube, boolean selectFirstIfNoValue)
  {
    return install(cb, key, selectFirstIfNoValue, true, new IndependentVariantsConfigurator(cube, descriptor, null));
  }

  public static ComboBoxController<ItemKey> install(AComboBox<ItemKey> cb, ModelKey<ItemKey> key,
    boolean selectFirstIfNoValue, boolean fixNonModelValue, VariantsConfigurator<ItemKey> variants) {
    ComboBoxController<ItemKey> controller = new ComboBoxController<ItemKey>(key, selectFirstIfNoValue, fixNonModelValue, variants);
    CONTROLLER.putClientValue(cb, controller);
    return controller;
  }
}
