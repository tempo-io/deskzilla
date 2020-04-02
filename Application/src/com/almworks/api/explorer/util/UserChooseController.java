package com.almworks.api.explorer.util;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.UIController;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.FilteringListDecorator;
import com.almworks.util.collections.Convertor;
import com.almworks.util.collections.Equality;
import com.almworks.util.commons.Condition;
import com.almworks.util.commons.Factory1;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.completion.CompletingComboBox;
import com.almworks.util.components.completion.CompletingComboBoxController;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.config.Configuration;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

/**
 * @author dyoma
 */
public class UserChooseController<T extends ItemKey> implements UIController<CompletingComboBox<T>> {
  private final VariantsConfigurator<ItemKey> myVariants;
  private final CompletingComboBoxController<T> myController;
  private final ModelKey<T> myKey;
  @SuppressWarnings({"RawUseOfParameterizedType"})
  private Factory1<Condition<T>, String> myFilterFactory;
  private Convertor<? super T, String> myToString;
  private Convertor<String, T> myFromString;
  @Nullable
  private Equality<T> myEquality;
  public static final Factory1<Condition<ItemKey>, String> DEFAULT_USER_FILTER =
    new Factory1<Condition<ItemKey>, String>() {
      public Condition<ItemKey> create(String argument) {
        final String typed = Util.lower(argument);
        return new Condition<ItemKey>() {
          public boolean isAccepted(ItemKey value) {
            return Util.lower(value.getId()).indexOf(typed) != -1 ||
              Util.lower(value.getDisplayName()).indexOf(typed) != -1;
          }
        };
      }
    };
  private CanvasRenderer<T> myRenderer;

  private UserChooseController(
    ModelKey<T> key, VariantsConfigurator<ItemKey> variants, CompletingComboBoxController<T> controller)
  {
    myKey = key;
    myVariants = variants;
    myController = controller;
  }

  public void connectUI(@NotNull Lifespan lifespan, @NotNull ModelMap model, @NotNull CompletingComboBox<T> component) {
    assert myToString != null;
    assert myFromString != null;
    assert myRenderer != null;
    ConnectContext context = new ConnectContext(lifespan, model);
    configureVariants(context);
    CompletingComboBoxController<T> controller = component.getController();
    if (myFilterFactory != null)
      controller.setFilterFactory(myFilterFactory);
    controller.setConvertors(myToString, myFromString, myEquality);
    controller.setCanvasRenderer(myRenderer);
    controller.setIdentityConvertor(ItemKey.GET_ID);
    SingleSelectionAccessorController.connectCB(context, controller.getModel(), new ConnectContext.Accessor(myKey), null,
      true, controller.getSetSelected());
  }

  private void configureVariants(final ConnectContext context) {
    myVariants.configure(context, new VariantsAcceptor<ItemKey>() {
      @Override
      public void accept(AListModel<ItemKey> variants, @Nullable Configuration recentConfig) {
        final FilteringListDecorator<ItemKey> enabledUsers =
          FilteringListDecorator.create(context.getLife(), variants, UIControllerUtil.IS_ENABLED);
        myController.setVariantsModel(recentConfig, (AListModel<? extends T>) enabledUsers);
      }
    });
  }

  public UserChooseController<T> setFilterFactory(Factory1<Condition<T>, String> filterFactory) {
    myFilterFactory = filterFactory;
    return this;
  }

  public UserChooseController<T> setStringConvertors(Convertor<? super T, String> toString, Convertor<String, T> fromString, Equality<T> equality) {
    myToString = toString;
    myFromString = fromString;
    myEquality = equality;
    return this;
  }

  public UserChooseController<T> setCanvasRenderer(CanvasRenderer<T> renderer) {
    myRenderer = renderer;
    return this;
  }

  public UserChooseController<T> setDefaultCanvasRenderer(ItemKey dispNameOnly) {
    return setCanvasRenderer((CanvasRenderer<T>) createDefaultUserRenderer(dispNameOnly));
  }

  public static <T extends ItemKey> UserChooseController<T> install(CompletingComboBox<T> comboBox, ModelKey<T> key,
    final BaseEnumConstraintDescriptor descriptor, final Convertor<ModelMap, ItemHypercube> cube, boolean excludeMissing)
  {
    VariantsConfigurator<ItemKey> variants = new IndependentVariantsConfigurator(cube, descriptor, ItemKey.DISPLAY_NAME_ORDER, !excludeMissing);
    UserChooseController<T> controller = new UserChooseController<T>(key, variants, comboBox.getController());
    CONTROLLER.putClientValue(comboBox, controller);
    return controller;
  }

  public static void defaultSetup(CompletingComboBoxController<ItemKey> controller, final ItemKey dispNameOnly) {
    controller.setConvertors(ItemKey.DISPLAY_NAME, ItemKey.INVALID_CREATOR);
    controller.setCanvasRenderer(createDefaultUserRenderer(dispNameOnly));
  }

  public static CanvasRenderer<ItemKey> createDefaultUserRenderer(final ItemKey dispNameOnly) {
    return new CanvasRenderer<ItemKey>() {
      public void renderStateOn(CellState state, Canvas canvas, ItemKey item) {
        if (Util.equals(dispNameOnly, item)) {
          canvas.appendText(item.getDisplayName());
          return;
        }
        String name = item.getDisplayName();
        canvas.appendText(name);
        String id = item.getId();
        if (!id.equals(name)) canvas.appendText(" (" + id + ")");
      }
    };
  }
}
