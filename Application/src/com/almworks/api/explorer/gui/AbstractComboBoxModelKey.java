package com.almworks.api.explorer.gui;

import com.almworks.api.application.*;
import com.almworks.api.edit.ItemCreator;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.TODO;
import com.almworks.util.advmodel.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.models.ColumnSizePolicy;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.threads.CanBlock;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.TypedKey;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.List;

import static com.almworks.explorer.qbuilder.filter.ItemKeyModelCollector.DERESOLVER;
import static com.almworks.util.collections.Containers.toUniqueSortedList;

/**
 * @author : Dyoma
 */
public abstract class AbstractComboBoxModelKey extends AttributeModelKey<ItemKey, Long> {
  private final ModelMergePolicy myMergePolicy;
  private final ItemKey myNullValue;
  private final TypedKey<VariantsModelFactory> myVariantsKey;
  private final TypedKey<ItemKey> myModelKey;

  @NotNull
  private final CanvasRenderer<PropertyMap> myCanvasRenderer = new CanvasRenderer<PropertyMap>() {
    @Override
    public void renderStateOn(CellState state, Canvas canvas, PropertyMap item) {
      ItemKey value = getValue(item);
      if (value == null) {
        assert false : getDisplayableName();
        return;
      }
      value.renderOn(canvas, state);
    }
  };
  private final ResolvedFactory<? extends ResolvedItem> myFactory;

  protected AbstractComboBoxModelKey(
    DBAttribute<Long> attribute, ItemKey nullValue, String displayableName, ModelMergePolicy mergePolicy,
    ResolvedFactory<? extends ResolvedItem> factory)
  {
    super(attribute, displayableName);
    myFactory = factory;
    myVariantsKey = TypedKey.create(getName() + "#variants");
    myModelKey = TypedKey.create(getName() + "#model");
    Threads.assertLongOperationsAllowed();
    myNullValue = nullValue;
    myMergePolicy = mergePolicy;
  }

  @CanBlock
  protected abstract VariantsModelFactory extractVariants(ItemVersion itemVersion, LoadedItemServices itemServices);

  protected abstract void addExistingChange(UserChanges changes);

  @Override
  public ItemKey getValue(ModelMap model) {
    return model.get(myModelKey);
  }

  @Override
  public <SM>SM getModel(final Lifespan lifespan, final ModelMap model, Class<SM> aClass) {
    checkSwingModelClass(aClass);
    AListModel<ItemKey> variants = attachVariants(model, lifespan);
    final SelectionInListModel<ItemKey> cbModel = attachSelectionInListModel(lifespan, model, variants);
    cbModel.addSelectionListener(lifespan, new SelectionListener.SelectionOnlyAdapter() {
      @Override
      public void onSelectionChanged() {
        if (!lifespan.isEnded()) {
          setModelValue(model, cbModel.getSelectedItem());
        }
      }
    });

    model.addAWTChangeListener(lifespan, new ChangeListener() {
      @Override
      public void onChange() {
        ItemKey selection = cbModel.getSelectedItem();
        ItemKey value = getValue(model);
        if (!isEqualValue(selection, value))
          cbModel.setSelectedItem(value);
      }
    });

    //noinspection unchecked
    return (SM) cbModel;
  }

  protected boolean isEqualValue(ItemKey value1, ItemKey value2) {
    return Util.equals(value1, value2);
  }

  @Override
  public boolean hasValue(ModelMap model) {
    return model.get(myVariantsKey) != null;
  }

  @Override
  public boolean isEqualValue(ModelMap models, PropertyMap values) {
    return hasValue(models) && isEqualValue(getValue(models), getValue(values));
  }

  @Override
  public boolean isEqualValue(PropertyMap values1, PropertyMap values2) {
    return isEqualValue(getValue(values1), getValue(values2));
  }

  @Override
  public void copyValue(final ModelMap to, PropertyMap from) {
    ItemKey newValue = getValue(from);
    to.put(myVariantsKey, from.get(myVariantsKey));
    to.registerKey(getName(), this);
    setModelValue(to, newValue);
  }

  @Override
  public void addChanges(UserChanges changes) {
    if (myMergePolicy == ModelMergePolicy.COPY_VALUES) {
      ItemKey newValue = changes.getNewValue(this);
      assert newValue == null : "Readonly key changed:" + this + " newValue:" + newValue;
      return;
    }
    if (getNullValueForModelMap().equals(changes.getNewValue(this))) {
      changes.getCreator().setValue(getAttribute(), (Long)null);
    } else {
      addExistingChange(changes);
    }
  }

  @Override
  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    Long attributeValue = itemVersion.getValue(getAttribute());
    ItemKey value = attributeValue == null ? null :
      getResolvedItem(itemServices.getItemKeyCache(), attributeValue, itemVersion.getReader());
    if (value == null) {
      value = getNullValueForModelMap();
    }
    setValue(values, value);
    values.put(myVariantsKey, extractVariants(itemVersion, itemServices));
  }

  @NotNull
  private ItemKey getNullValueForModelMap() {
    return myNullValue == null ? ItemKeyStub.ABSENT : myNullValue;
  }

  @Nullable
  protected ResolvedItem getResolvedItem(ItemKeyCache cache, long item, DBReader reader) {
    return cache.getItemKeyOrNull(item, reader, myFactory);
  }

  @Override
  @NotNull
  public CanvasRenderer<PropertyMap> getRenderer() {
    return myCanvasRenderer;
  }

  @Override
  public void takeSnapshot(PropertyMap to, ModelMap from) {
    setValue(to, getValue(from));
    to.put(myVariantsKey, from.get(myVariantsKey));
  }

  @Override
  public ModelMergePolicy getMergePolicy() {
    return myMergePolicy;
  }

  public boolean allowsAbsent() {
    return myNullValue != null;
  }

  @Override
  public <V> ModelOperation<V> getOperation(TypedKey<V> key) {
    if(ModelOperation.SET_STRING_VALUE.equals(key)) {
      return (ModelOperation)new SetStringValueOperation();
    }

    if(ModelOperation.SET_ITEM_KEY.equals(key)) {
      return (ModelOperation)new SetArtifactKeyOperation();
    }

    return super.getOperation(key);
  }

  @Override
  public ColumnSizePolicy getRendererSizePolicy() {
    return ColumnSizePolicy.FREE;
  }

  /** @return unsorted list of resolved variants */
  public List<ResolvedItem> getApplicableVariantsList(PropertyMap values) {
    return getResolvedVariants(values);
  }

  public List<ResolvedItem> getResolvedVariants(PropertyMap values) {
    VariantsModelFactory factory = values.get(myVariantsKey);
    assert factory != null;
    return factory.getResolvedList();
  }

  public List<ItemKey> getVariantsList(ModelMap model) {
    VariantsModelFactory factory = model.get(myVariantsKey);
    assert factory != null;
    return toUniqueSortedList(DERESOLVER.collectList(factory.getResolvedList()));
  }

  public void setModelValue(final ModelMap model, @Nullable ItemKey newValue) {
    ItemKey prevValue = model.get(myModelKey);
    if (!isEqualValue(prevValue, newValue)) {
      model.put(myModelKey, newValue);
      model.valueChanged(this);
    }
  }

  protected SelectionInListModel<ItemKey> attachSelectionInListModel(Lifespan lifespan, final ModelMap model,
    AListModel<ItemKey> variants)
  {
    boolean isNew = Boolean.TRUE.equals(model.get(ItemCreator.NEW_ITEM));
    ItemKey initialValue = getValue(model);
    if (isNew) {
      if (!variants.contains(initialValue)) initialValue = variants.getSize() > 0 ? variants.getAt(0) : initialValue;
    }
    return SelectionInListModel.create(lifespan, variants, initialValue);
  }

  public AListModel<ItemKey> attachVariants(final ModelMap model, Lifespan life) {
    VariantsModelFactory factory = model.get(myVariantsKey);
    assert factory != null;
    return factory.createModel(life);
  }

  protected void checkSwingModelClass(Class<?> aClass) {
    if (!aClass.isAssignableFrom(AComboboxModel.class))
      throw TODO.shouldNotHappen(aClass.getName());
  }

  private class SetStringValueOperation implements ModelOperation<String> {
    @Override
    public void perform(ItemUiModel model, String argument) throws CantPerformExceptionExplained {
      List<ItemKey> variants = getVariantsList(model.getModelMap());
      for (ItemKey variant : variants) {
        if (variant.getId().equals(argument)) {
          setModelValue(model.getModelMap(), variant);
          return;
        }
      }
      throw new CantPerformExceptionExplained("Unknown value: " + argument);
    }

    @Override
    public String getArgumentProblem(String argument) {
      assert false : "not implemented yet";
      return "";
    }
  }

  private class SetArtifactKeyOperation implements ModelOperation<ItemKey> {
    @Override
    public void perform(ItemUiModel model, ItemKey argument) throws CantPerformExceptionExplained {
      assert getArgumentProblem(argument) == null : argument;
      if (argument == null)
        argument = myNullValue;
      if (argument == null)
        throw new CantPerformExceptionExplained("Cannot set empty value [" + this + "]");
      setModelValue(model.getModelMap(), argument);
    }

    @Override
    public String getArgumentProblem(ItemKey argument) {
      return argument != null || allowsAbsent() ? null : "";
    }
  }
}
