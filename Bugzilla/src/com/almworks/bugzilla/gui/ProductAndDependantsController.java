package com.almworks.bugzilla.gui;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.UIController;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.bugzilla.provider.meta.ProductDependenciesKey;
import com.almworks.spi.provider.AbstractConnection;
import com.almworks.util.advmodel.*;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.*;
import com.almworks.util.components.recent.CBRecentSynchronizer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.GlobalColors;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifecycle;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.Set;

class ProductAndDependantsController implements UIController<AComboBox> {
  private final AComboBox<ItemKey> myProduct;
  private final AComboBox<ItemKey> myComponent;
  private final AComboBox<ItemKey> myVersion;
  private final AComboBox<ItemKey> myMilestone;
  private final boolean myAdjustProductValue;

  private ProductAndDependantsController(
    AComboBox product, AComboBox component, AComboBox version, AComboBox milestone, boolean adjustProductValue)
  {
    myProduct = product;
    myComponent = component;
    myVersion = version;
    myMilestone = milestone;
    myAdjustProductValue = adjustProductValue;
  }

  public void connectUI(Lifespan lifespan, ModelMap model, AComboBox component) {
    // all four have tangled side effects
    final AComboboxModel<ItemKey> products = getProductsModel(lifespan, model);
    final AComboboxModel<ItemKey> components = getComponentsModel(lifespan, model);
    final AComboboxModel<ItemKey> versions = getVersionsModel(lifespan, model);
    final AComboboxModel<ItemKey> milestones = getMilestonesModel(lifespan, model);

    final ProductDependenciesTracker tracker = ProductDependenciesKey.KEY.getValue(model);
    if(products != null && tracker != null) {
      listenToProductSelection(lifespan, products, components, versions, milestones, tracker);
    }

    if(products instanceof SelectionInListModel) {
      listenToConnectionConfiguration(lifespan, model, products);
    }
  }

  @Nullable
  private AComboboxModel<ItemKey> getProductsModel(Lifespan lifespan, ModelMap model) {
    return setupCombobox(myProduct, BugzillaKeys.product, model, lifespan, false);
  }

  @Nullable
  private AComboboxModel<ItemKey> getComponentsModel(Lifespan lifespan, ModelMap model) {
    return setupCombobox(myComponent, BugzillaKeys.component, model, lifespan, false);
  }

  @Nullable
  private AComboboxModel<ItemKey> getVersionsModel(Lifespan lifespan, ModelMap model) {
    return setupCombobox(myVersion, BugzillaKeys.version, model, lifespan, false);
  }

  @Nullable
  private AComboboxModel<ItemKey> getMilestonesModel(Lifespan lifespan, ModelMap model) {
    return setupCombobox(myMilestone, BugzillaKeys.milestone, model, lifespan, true);
  }

  @Nullable
  private AComboboxModel<ItemKey> setupCombobox(
    AComboBox<ItemKey> combobox, ModelKey<ItemKey> key, ModelMap model,
    Lifespan lifespan, boolean useMilestoneRenderer)
  {
    assert key != null;

    final AComboboxModel<ItemKey> original = key.getModel(lifespan, model, AComboboxModel.class);
    if(original == null) {
      return null;
    }

    final BugzillaConnection connection = BugzillaConnection.getInstance(model);
    if(connection == null) {
      return null;
    }

    final SelectionInListModel<ItemKey> filtered = getFilteredModel(original, lifespan);
    final AComboboxModel<ItemKey> result = Util.NN(filtered, original);
    
    final CanvasRenderer<ItemKey> renderer = useMilestoneRenderer ?
      new MilestoneHighlightingRenderer(filtered) : new ErrorHighlightingRenderer(filtered);

    CBRecentSynchronizer.setupComboBox(
      lifespan, combobox, result,
      connection.getConnectionConfig(AbstractConnection.RECENTS, key.getName()),
      ItemKey.GET_ID, renderer);

    return result;
  }

  @Nullable
  private SelectionInListModel<ItemKey> getFilteredModel(
    AComboboxModel<ItemKey> original, Lifespan lifespan)
  {
    if(original instanceof SelectionInListModel) {
      return decorateSelectionModel((SelectionInListModel)original, lifespan);
    }
    return null;
  }

  private SelectionInListModel<ItemKey> decorateSelectionModel(
    SelectionInListModel<ItemKey> model, Lifespan lifespan)
  {
    final FilteringListDecorator<ItemKey> decorator = FilteringListDecorator.create(model.getData());
    decorator.setFilter(Condition.always());
    lifespan.add(decorator.getDetach());
    model.setData(lifespan, decorator);
    return model;
  }

  private void listenToProductSelection(
    Lifespan lifespan,
    final AComboboxModel<ItemKey> products, final AComboboxModel<ItemKey> components,
    final AComboboxModel<ItemKey> versions, final AComboboxModel<ItemKey> milestones,
    final ProductDependenciesTracker tracker)
  {
    final Lifecycle[] filterLives = { new Lifecycle(), new Lifecycle(), new Lifecycle() };
    for(final Lifecycle life : filterLives) {
      lifespan.add(life.getDisposeDetach());
    }
    
    final SelectionListener.SelectionOnlyAdapter listener = new SelectionListener.SelectionOnlyAdapter() {
      public void onSelectionChanged() {
        Threads.assertAWTThread();
        final ItemKey selection = products.getSelectedItem();
        final ProductDependencyInfo info = tracker.getInfo(selection);
        setVariantsFilter(components, info, BugzillaAttribute.COMPONENT, filterLives[0]);
        setVariantsFilter(versions, info, BugzillaAttribute.VERSION, filterLives[1]);
        setVariantsFilter(milestones, info, BugzillaAttribute.TARGET_MILESTONE, filterLives[2]);
        myComponent.repaint();
        myVersion.repaint();
        myMilestone.repaint();
      }
    };

    listener.onSelectionChanged();
    products.addSelectionListener(lifespan, listener);
  }

  private void setVariantsFilter(
    AComboboxModel<ItemKey> model, ProductDependencyInfo info, BugzillaAttribute attribute, Lifecycle filterLife)
  {
    final FilteringListDecorator<ItemKey> variants = extractFilteringDecorator(model);
    if(variants != null) {
      if(info == null) {
        variants.setFilter(Condition.<ItemKey>always());
      } else {
        info.filterVariants(variants, attribute, filterLife);
      }
    }
  }

  @Nullable
  private FilteringListDecorator<ItemKey> extractFilteringDecorator(@Nullable AComboboxModel<ItemKey> model) {
    if(model instanceof SelectionInListModel) {
      final AListModel listModel = ((SelectionInListModel<ItemKey>)model).getData();
      if(listModel instanceof FilteringListDecorator) {
        return (FilteringListDecorator<ItemKey>)listModel;
      }
    }
    return null;
  }

  private void listenToConnectionConfiguration(
    Lifespan lifespan, ModelMap model, final AComboboxModel<ItemKey> products)
  {
    final BugzillaContext context = BugzillaUtil.getContext(model);
    if(context != null) {
      final ScalarModel<OurConfiguration> configuration = context.getConfiguration();
      configuration.getEventSource().addAWTListener(
        lifespan,
        new ScalarModel.Adapter<OurConfiguration>() {
          public void onScalarChanged(ScalarModelEvent<OurConfiguration> event) {
            setProductsFilter(products, event.getNewValue());
          }
        });
    }
  }

  private void setProductsFilter(final AComboboxModel<ItemKey> products, OurConfiguration config) {
    final FilteringListDecorator<ItemKey> variants = extractFilteringDecorator(products);
    if(variants != null) {
      variants.setFilter(getLimitingProductsFilter(config));
      if(myAdjustProductValue) {
        adjustProductValue(products);
      }
    }
  }

  private Condition<ItemKey> getLimitingProductsFilter(OurConfiguration config) {
    if(config.isLimitByProduct()) {
      final String[] limitingProducts = config.getLimitingProducts();
      if(limitingProducts.length > 0) {
        final Set<String> allowed = Collections15.hashSet(limitingProducts);
        return new Condition<ItemKey>() {
          public boolean isAccepted(ItemKey key) {
            return allowed.contains(key.getDisplayName());
          }
        };
      }
    }
    return Condition.always();
  }

  private void adjustProductValue(AComboboxModel<ItemKey> products) {
    final Object selectedProduct = products.getSelectedItem();
    if(products.indexOf(selectedProduct) == -1 && products.getSize() > 0) {
      products.setSelectedItem(products.getAt(0));
    }
  }

  private static class ErrorHighlightingRenderer implements CanvasRenderer<ItemKey> {
    protected final AComboboxModel<ItemKey> myFilteredModel;

    public ErrorHighlightingRenderer(AComboboxModel<ItemKey> filteredModel) {
      myFilteredModel = filteredModel;
    }

    @Override
    public void renderStateOn(CellState state, Canvas canvas, ItemKey item) {
      if(item != null) {
        item.renderOn(canvas, state);
        if(state.isExtracted() && mustHighlight(item)) {
          canvas.setFontStyle(Font.BOLD);
          canvas.setForeground(GlobalColors.ERROR_COLOR);
        }
      }
    }

    protected boolean mustHighlight(ItemKey item) {
      return isItemAbsentFromModel(item);
    }

    protected final boolean isItemAbsentFromModel(ItemKey item) {
      return myFilteredModel != null && myFilteredModel.indexOf(item) < 0;
    }
  }

  private static class MilestoneHighlightingRenderer extends ErrorHighlightingRenderer {
    public MilestoneHighlightingRenderer(AComboboxModel<ItemKey> filteredModel) {
      super(filteredModel);
    }

    @Override
    protected boolean mustHighlight(ItemKey item) {
      return isItemAbsentFromModel(item) && !isSingleNoMilestone(item);
    }

    private boolean isSingleNoMilestone(ItemKey item) {
      return myFilteredModel != null && myFilteredModel.getSize() == 0
        && item.getDisplayName().equals(BugzillaAttribute.NO_MILESTONE);
    }
  }

  public static void install(
    AComboBox product, AComboBox component, AComboBox version, AComboBox milestone, boolean adjustProductValue)
  {
    final ProductAndDependantsController c =
      new ProductAndDependantsController(product, component, version, milestone, adjustProductValue);
    UIController.CONTROLLER.putClientValue(product, c);
    UIController.CONTROLLER.putClientValue(component, UIController.NUMB);
    UIController.CONTROLLER.putClientValue(version, UIController.NUMB);
    UIController.CONTROLLER.putClientValue(milestone, UIController.NUMB);
  }
}
