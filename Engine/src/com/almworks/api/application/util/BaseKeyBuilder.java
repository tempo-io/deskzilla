package com.almworks.api.application.util;

import com.almworks.api.application.*;
import com.almworks.api.application.qb.EnumConstraintType;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.items.api.*;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.collections.Factories;
import com.almworks.util.commons.Factory;
import com.almworks.util.commons.Function;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.models.ColumnSizePolicy;
import com.almworks.util.models.TableColumnAccessor;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.threads.CanBlock;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author dyoma
 */
public class BaseKeyBuilder<T> implements KeyBuilder<T> {
  private String myName;
  private String myDisplayName;
  private ModelMergePolicy myMergePolicy = ModelMergePolicy.MANUAL;
  private ColumnSizePolicy mySizePolicy = ColumnSizePolicy.FREE;
  private Comparator<T> myComparator;
  private String myHeaderText;
  private CanvasRenderer<PropertyMap> myAllValuesRenderer;
  private CanvasRenderer<? super T> myValueRenderer;
  @SuppressWarnings({"unchecked"})
  private final BaseModelKey<T>[] myKey = new BaseModelKey[1];
  private CanvasRenderable myNullRenderable = CanvasRenderable.EMPTY;
  private boolean myHasColumn = false;
  private int myCharCount = -1;
  private boolean myColumnAllowsDummy = true;
  private Factory<? extends BaseModelKey.DataIO<T>> myIO = null;
  private BaseModelKey.Export<? super T> myExport = null;
  private BaseModelKey.DataAccessor<T> myAccessor = null;
  private DataPromotionPolicy myPromotionPolicy = DataPromotionPolicy.STANDARD;
  private Map myOptionalBehaviors;

  private BaseKeyBuilder() {
  }

  @NotNull
  public ModelKey<T> getKey() {
    if (myKey[0] != null)
      return myKey[0];
    assert myName != null;
    assert myDisplayName != null : myName;
    assert myMergePolicy != null : myDisplayName;
    assert mySizePolicy != null : myDisplayName;
    assert myIO != null : myDisplayName;
    assert myPromotionPolicy != null : myDisplayName;
    myKey[0] = new BaseModelKey<T>(myName, myDisplayName, getRenderer(), myMergePolicy, mySizePolicy, myComparator,
      myIO.create(), myExport, myAccessor, myPromotionPolicy, myOptionalBehaviors);
    return myKey[0];
  }

  @Nullable
  public TableColumnAccessor<LoadedItem, ?> getColumn(ModelKey<Boolean> dummyKey,
    Comparator<LoadedItem> defaultOrder)
  {
    if (!myHasColumn)
      return null;
    assert myHeaderText != null : myDisplayName;
    assert myCharCount > 0 : myDisplayName;
    return new ValueColumn<T>(getKey(), defaultOrder, myComparator, myCharCount,
      (myColumnAllowsDummy ? dummyKey : null), myHeaderText);
  }


  public BaseKeyBuilder<T> setName(String name) {
    myName = name;
    return this;
  }

  public BaseKeyBuilder<T> setDisplayName(String displayName) {
    myDisplayName = displayName;
    return this;
  }

  public BaseKeyBuilder<T> setMergePolicy(ModelMergePolicy mergePolicy) {
    myMergePolicy = mergePolicy;
    return this;
  }

  public BaseKeyBuilder<T> setPromotionPolicy(@NotNull DataPromotionPolicy promotionPolicy) {
    //noinspection ConstantConditions
    if (promotionPolicy == null) {
      assert false;
      return this;
    }
    myPromotionPolicy = promotionPolicy;
    return this;
  }

  public void setSizePolicy(ColumnSizePolicy sizePolicy) {
    mySizePolicy = sizePolicy;
  }

  public BaseKeyBuilder<T> setComparator(Comparator<T> comparator) {
    myComparator = comparator;
    return this;
  }

  public void setHeaderText(String headerText) {
    myHeaderText = headerText;
  }

  public void setAllValuesRenderer(CanvasRenderer<PropertyMap> allValuesRenderer) {
    myAllValuesRenderer = allValuesRenderer;
  }

  public BaseKeyBuilder<T> setValueRenderer(CanvasRenderer<? super T> valueRenderer) {
    myValueRenderer = valueRenderer;
    return this;
  }

  public void setTextCachingValueRenderer(final CanvasRenderer<? super T> valueRenderer) {
    final BaseModelKey<T>[] key = myKey;
    setAllValuesRenderer(new CanvasRenderer<PropertyMap>() {
      private final TypedKey<PlainTextCanvas> myCacheKey = TypedKey.create("" + getName() + ":cache");

      public void renderStateOn(CellState state, Canvas canvas, PropertyMap item) {
        PlainTextCanvas cachedCanvas = OptionalCache.getCachedValue(myCacheKey, item);
        if (cachedCanvas == null) {
          cachedCanvas = new PlainTextCanvas();
          valueRenderer.renderStateOn(state, cachedCanvas, key[0].getValue(item));
          OptionalCache.cacheValue(myCacheKey, item, cachedCanvas);
        }
        cachedCanvas.copyTo(canvas);
      }
    });
  }

  public <V> BaseKeyBuilder<T> setOptionalBehavior(@NotNull TypedKey<V> key, @NotNull V behavior) {
    if (myOptionalBehaviors == null)
      myOptionalBehaviors = Collections15.hashMap();
    key.putTo(myOptionalBehaviors, behavior);
    return this;
  }

  public BaseKeyBuilder<T> setNullRenderable(CanvasRenderable nullRenderable) {
    myNullRenderable = nullRenderable;
    return this;
  }

  public void setSimpleIO(DBAttribute<T> attribute) {
    setIO(new SimpleIO<T>(attribute));
  }

  public void setSimpleRefIO(DBAttribute<Long> attribute, ResolvedFactory factory, StateIconFactory stateIconFactory,
    EnumConstraintType constraintType, boolean ignoreUserChanges)
  {
    RefIOBuilder builder = buildRefIO(attribute, factory);
    builder.setConstraintType(constraintType);
    builder.setIconFactory(stateIconFactory);
    builder.setIgnoreUserChanges(ignoreUserChanges);
  }

  public RefIOBuilder buildRefIO(DBAttribute<Long> attribute, ResolvedFactory factory) {
    RefIOBuilder builder = new RefIOBuilder(attribute, factory);
    myIO = (Factory) builder;
    return builder;
  }

  public void setSimpleRefListIO(DBAttribute<List<Long>> attribute, ResolvedFactory factory, @Nullable DBIdentifiedObject ignore,
    EnumConstraintType type, Function<UserChanges, ItemHypercube> valueHypercubeFunction, boolean opimizeList)
  {
    setIO((BaseModelKey.DataIO<T>)
      new SimpleRefListIO(attribute, factory, ignore, type, valueHypercubeFunction, opimizeList));
  }

  public BaseKeyBuilder<T> setIO(BaseModelKey.DataIO<T> io) {
    myIO = Factories.singleton(io);
    return this;
  }

  public BaseKeyBuilder<T> setAccessor(BaseModelKey.DataAccessor<T> accessor) {
    myAccessor = accessor;
    return this;
  }

  public BaseKeyBuilder<T> setExport(BaseModelKey.Export<? super T> export) {
    myExport = export;
    return this;
  }

  public BaseKeyBuilder<T> column(@Nullable String headerText, int charCount) {
    myHasColumn = true;
    myColumnAllowsDummy = true;
    myHeaderText = headerText != null ? headerText : myDisplayName;
    myCharCount = charCount;
    return this;
  }

  public BaseKeyBuilder<T> noDummyColumn() {
    myColumnAllowsDummy = false;
    return this;
  }

  @NotNull
  protected String getColumnHeaderText() {
    return myHeaderText != null ? myHeaderText : myDisplayName;
  }

  @NotNull
  private CanvasRenderer<PropertyMap> getRenderer() {
    if (myAllValuesRenderer != null)
      return myAllValuesRenderer;
    final BaseModelKey<T>[] key = myKey;
    return myValueRenderer != null ? createValueRenderer(key, myValueRenderer) :
      createGeneralRenderer(key, myNullRenderable);
  }

  private static <T> CanvasRenderer<PropertyMap> createGeneralRenderer(final BaseModelKey<T>[] key,
    final CanvasRenderable nullRenderer)
  {
    return new CanvasRenderer<PropertyMap>() {

      public void renderStateOn(final CellState state, final Canvas canvas, PropertyMap item) {
        T value = key[0].getValue(item);
        if (value == null) {
          nullRenderer.renderOn(canvas, state);
        } else if (value instanceof CanvasRenderable)
          ((CanvasRenderable) value).renderOn(canvas, state);
        else {
          assert value instanceof String || value instanceof Integer : value;
          canvas.appendText(value.toString());
        }
      }
    };
  }


  private static <T> CanvasRenderer<PropertyMap> createValueRenderer(final BaseModelKey<T>[] key,
    final CanvasRenderer<? super T> valueRenderer)
  {
    return new CanvasRenderer<PropertyMap>() {
      public void renderStateOn(CellState state, Canvas canvas, PropertyMap item) {
        valueRenderer.renderStateOn(state, canvas, key[0].getValue(item));
      }
    };
  }

  public static <T> BaseKeyBuilder<T> create() {
    return new BaseKeyBuilder<T>();
  }

  public String getName() {
    return myName;
  }

  public static class SimpleIO<V> extends BaseSimpleIO<V, V> {
    public SimpleIO(DBAttribute<V> attribute) {
      super(attribute);
    }

    @Override
    protected V extractValue(V dbValue, ItemVersion version, LoadedItemServices itemServices) {
      return dbValue;
    }

    @Override
    @NotNull
    protected V toDatabaseValue(UserChanges changes, V userInput) {
      return userInput;
    }
  }

  public static abstract class RefCollectionIO<CE extends Collection<ItemKey>, CSE extends Collection<Long>>
    extends BaseSimpleIO.CollectionIO<ItemKey, Long, CE, CSE>
  {
    protected final ResolvedFactory myFactory;
    protected final DBIdentifiedObject myIgnore;
    protected final EnumConstraintType myConstraintType;
    protected final Function<UserChanges, ItemHypercube> myValueHypercubeFunction;
    private final boolean myOptimize;

    public RefCollectionIO(DBAttribute<CSE> attribute, ResolvedFactory factory, DBIdentifiedObject ignore,
      EnumConstraintType type)
    {
      this(attribute, factory, ignore, type, null, true);
    }

    public RefCollectionIO(DBAttribute<CSE> attribute, ResolvedFactory factory, DBIdentifiedObject ignore,
      EnumConstraintType type, @Nullable Function<UserChanges, ItemHypercube> valueHypercubeFunction,
      boolean optimize)
    {
      super(attribute);
      myFactory = factory;
      myIgnore = ignore;
      myConstraintType = type;
      myValueHypercubeFunction = valueHypercubeFunction;
      myOptimize = optimize;
    }

    public EnumConstraintType getConstraintType() {
      return myConstraintType;
    }

    @Override
    protected CE extractValue(CSE dbValue, ItemVersion version, LoadedItemServices itemServices) {
      if(dbValue == null || dbValue.isEmpty()) {
        return emptyCollection();
      }

      CE result = newCollection(dbValue.size());

      final ItemKeyCache cache = itemServices.getItemKeyCache();
      final DBReader reader = version.getReader();
      final long ignoreId = myIgnore != null ? reader.findMaterialized(myIgnore) : 0;
      for(final Long item : dbValue) {
        if(myIgnore == null || item != ignoreId) {
          try {
            ResolvedItem key = cache.getItemKey(item, reader, myFactory);
            result.add(key);
          } catch(BadItemException e) {
            // ignore
          }
        }
      }

      if(myOptimize) {
        result = optimize(itemServices, result);
      }

      return result;
    }

    protected abstract CE newCollection(int sizeHint);

    protected abstract CE optimize(LoadedItemServices services, CE result);

    @Override
    @CanBlock
    protected CSE toDatabaseValue(UserChanges changes, CE userInput) {
      if(userInput == null) {
        return null;
      }
      if(userInput.isEmpty()) {
        return emptyDbCollection();
      }

      final LinkedHashSet<Long> result = Collections15.linkedHashSet();
      final ItemHypercube cube = getValueHypercube(changes);
      for(final ItemKey key : userInput) {
        final Collection<ResolvedItem> resolvedKeys = resolveKey(key, cube, changes);
        final int count = resolvedKeys.size();
        if(count != 1) {
          if(count == 0) {
            Log.warn("no items for key " + key);
          } else {
            Log.warn("multiple items for key " + key + ": " + resolvedKeys);
          }
        }
        for(final ResolvedItem resolvedKey : resolvedKeys) {
          final long item = resolvedKey.getResolvedItem();
          if(item > 0) {
            result.add(item);
          }
        }
      }

      return convertSet(result);
    }

    protected abstract CSE emptyDbCollection();

    protected abstract CSE convertSet(Set<Long> result);

    protected Collection<ResolvedItem> resolveKey(ItemKey key, ItemHypercube cube, UserChanges changes) {
      return myConstraintType.resolveKey(key, cube);
    }

    private ItemHypercube getValueHypercube(UserChanges changes) {
      Function<UserChanges, ItemHypercube> function = myValueHypercubeFunction;
      ItemHypercube cube = null;
      if (function != null) {
        cube = function.invoke(changes);
      }
      if (cube == null) {
        cube = changes.createConnectionCube();
      }
      return cube;
    }
  }

  public static class SimpleRefListIO extends RefCollectionIO<List<ItemKey>, List<Long>> {
    public SimpleRefListIO(DBAttribute<List<Long>> attribute, ResolvedFactory factory, DBIdentifiedObject ignore,
      EnumConstraintType type)
    {
      super(attribute, factory, ignore, type, null, true);
    }

    public SimpleRefListIO(DBAttribute<List<Long>> attribute, ResolvedFactory factory, DBIdentifiedObject ignore,
      EnumConstraintType type, @Nullable Function<UserChanges, ItemHypercube> valueHypercubeFunction,
      boolean optimizeList)
    {
      super(attribute, factory, ignore, type, valueHypercubeFunction, optimizeList);
    }

    @Override
    protected List<ItemKey> emptyCollection() {
      return Collections15.emptyList();
    }

    @Override
    protected List<ItemKey> newCollection(int sizeHint) {
      return Collections15.arrayList(sizeHint);
    }

    @Override
    protected List<ItemKey> optimize(LoadedItemServices services, List<ItemKey> result) {
      return ModelKeyUtils.getOptimizedEnumList(services, result);
    }

    @Override
    protected List<Long> emptyDbCollection() {
      return Collections15.emptyList();
    }

    @Override
    protected List<Long> convertSet(Set<Long> result) {
      return Collections15.arrayList(result);
    }
  }

  public static class SimpleRefSetIO extends RefCollectionIO<Set<ItemKey>, Set<Long>> {
    public SimpleRefSetIO(DBAttribute<Set<Long>> attribute, ResolvedFactory factory, DBIdentifiedObject ignore,
      EnumConstraintType type)
    {
      super(attribute, factory, ignore, type, null, true);
    }

    public SimpleRefSetIO(DBAttribute<Set<Long>> attribute, ResolvedFactory factory, DBIdentifiedObject ignore,
      EnumConstraintType type, @Nullable Function<UserChanges, ItemHypercube> valueHypercubeFunction,
      boolean optimizeList)
    {
      super(attribute, factory, ignore, type, valueHypercubeFunction, optimizeList);
    }

    @Override
    protected Set<ItemKey> emptyCollection() {
      return Collections15.emptySet();
    }

    @Override
    protected Set<ItemKey> newCollection(int sizeHint) {
      return Collections15.hashSet(sizeHint);
    }

    @Override
    protected Set<ItemKey> optimize(LoadedItemServices services, Set<ItemKey> result) {
      return ModelKeyUtils.getOptimizedEnumSet(services, result);
    }

    @Override
    protected Set<Long> emptyDbCollection() {
      return Collections15.emptySet();
    }

    @Override
    protected Set<Long> convertSet(Set<Long> result) {
      return result;
    }
  }
}