package com.almworks.explorer.qbuilder.filter;

import com.almworks.api.application.*;
import com.almworks.api.application.qb.*;
import com.almworks.api.application.tree.UserQueryNode;
import com.almworks.api.constraint.Constraint;
import com.almworks.api.engine.Connection;
import com.almworks.api.explorer.util.ItemKeys;
import com.almworks.api.syncreg.*;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.dp.DPNotNull;
import com.almworks.util.advmodel.*;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.*;
import com.almworks.util.commons.Procedure;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.properties.*;
import com.almworks.util.text.parser.*;
import com.almworks.util.threads.*;
import org.almworks.util.*;
import org.almworks.util.detach.DetachComposite;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;
import util.concurrent.Synchronized;

import javax.swing.*;
import java.util.*;

/**
 * @author dyoma
 */
public class BaseEnumConstraintDescriptor extends AbstractConstraintDescriptor
  implements EnumConstraintType, CanvasRenderable
{
  private final DBAttribute myAttribute;
  private final DetachComposite myLife = new DetachComposite();
  private final EnumNarrower myNarrower;

  private final PropertyKey<OrderListModel<ItemKey>, List<ItemKey>> myModelKey;

  private final Synchronized<ResolvedItem> myNotSetItem = new Synchronized<ResolvedItem>(null);
  @Nullable
  private final DBIdentifiedObject myNotSetObject;

  private final String myDisplayableName;
  private final String myId;

  private final SimpleModifiable myModifiable = new SimpleModifiable();

  private final EnumConstraintKind myKind;
  private final ItemKeyModelCollector<? extends ResolvedItem> myItemKeys;

  private final List<EnumGrouping> myGroupings;
  private final Convertor<ItemKey,String> myFilterConvertor;
  private final Comparator<? super ItemKey> myOrder;
  private final CanvasRenderer<ItemKey> myVariantsRenderer;

  private final boolean mySearchSubstring;
  private Icon myIcon;

  private BaseEnumConstraintDescriptor(DBAttribute attribute, EnumNarrower narrower, String displayableName, @Nullable DBIdentifiedObject notSetObject, EnumConstraintKind kind,
    @Nullable List<EnumGrouping> groupings, Convertor<ItemKey,String> filterConvertor, @Nullable Comparator<? super ItemKey> order, @Nullable CanvasRenderer<ItemKey> variantsRenderer,
    boolean searchSubstring, String id, ItemKeyModelCollector<?> modelCollector)
  {
    myOrder = order;
    myGroupings = groupings;
    myFilterConvertor = filterConvertor;
    myDisplayableName = displayableName;
    myAttribute = attribute;
    myKind = kind;
    myId = id;
    myModelKey = new SubsetKey("subset", myId);
    myNarrower = narrower;
    myItemKeys = modelCollector;
    myNotSetObject = notSetObject;
    myVariantsRenderer = variantsRenderer != null ? variantsRenderer :
      new Renderers.DefaultCanvasRenderer<ItemKey>(EMPTY);
    mySearchSubstring = searchSubstring;
    getAllVariantsModel().addChangeListener(myLife, myModifiable);
  }

  public void setIcon(Icon icon) {
    myIcon = icon;
  }

  public static PropertyMap createValues(List<ItemKey> subset) {
    PropertyMap values = new PropertyMap();
    values.put(SUBSET, subset);
    return values;
  }

  public void renderOn(Canvas canvas, CellState state) {
    if (myIcon != null)
      canvas.setIcon(myIcon);
    else
      canvas.setIcon(myKind.getIcon());
    canvas.appendText(myDisplayableName);
  }

  public String getDisplayName() {
    return myDisplayableName;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  public ConstraintType getType() {
    return this;
  }

  @NotNull
  public ConstraintDescriptor resolve(NameResolver resolver, @Nullable ItemHypercube cube, @Nullable PropertyMap data) {
    // todo #1183
    // todo re-resolve items always -
    if (data != null) {
      List<ItemKey> subset = data.get(SUBSET);
      List<ItemKey> subsetReplacement = null;
      if (subset != null) {
        for (int i = 0; i < subset.size(); i++) {
          ItemKey key = subset.get(i);
          long resolved = key.getResolvedItem();
          if (resolved != 0) {
            if (subsetReplacement != null) {
              subsetReplacement.add(key);
            }
          } else {
            if (subsetReplacement == null) {
              subsetReplacement = Collections15.arrayList();
              if (i > 0) {
                subsetReplacement.addAll(subset.subList(0, i));
              }
            }
            boolean resolutionsFound = resolve(key.getId(), cube, subsetReplacement);
            if (!resolutionsFound) {
              // keep unresolved key
              subsetReplacement.add(key);
            }
          }
        }
      }
      if (subsetReplacement == null) {
        List<ItemKey> changed = removeDuplicates(subset);
        if (changed != subset) {
          subsetReplacement = subset;
        }
      } else {
        subsetReplacement = removeDuplicates(subsetReplacement);
      }
      if (subsetReplacement != null) {
        // renaming makes this assertion fire
//        assert Util.equals(getUniqueKeys(SUBSET.getFrom(data)), getUniqueKeys(subsetReplacement)) :
//          "old:" + SUBSET.getFrom(data) + " new:" + getUniqueKeys(subsetReplacement);
        data.replace(SUBSET, subsetReplacement);
      }
    }
    return this;
  }

  // works on O(n^2), to conserve memory
  static List<ItemKey> removeDuplicates(List<ItemKey> subset) {
    if (subset == null || subset.size() < 2)
      return subset;
    int size = subset.size();
    // check
    boolean filter = false;
    for (int i = 1; i < size; i++) {
      ItemKey key1 = subset.get(i);
      long r1 = key1.getResolvedItem();
      String id1 = key1.getId();
      for (int j = 0; j < i; j++) {
        ItemKey key2 = subset.get(j);
        if (r1 != 0) {
          if (r1 == key2.getResolvedItem()) {
            filter = true;
            break;
          }
        } else {
          if (Util.equals(id1, key2.getId())) {
            filter = true;
            break;
          }
        }
      }
    }
    if (!filter)
      return subset;
    List<ItemKey> result = Collections15.arrayList(subset.size());
    for (int i = 0; i < size; i++) {
      ItemKey key = subset.get(i);
      long r = key.getResolvedItem();
      if (r != 0) {
        // resolved is added if there are no other resolved in the result with the same artifact
        if (!containsArtifact(result, r)) {
          result.add(key);
        }
      } else {
        // unresolved is added if there are no other equal-by-id unresolved
        // but if there's an overriding resolved key, use it instead
        if (!result.contains(key)) {
          // check forward for same-named resolved value
          int found = -1;
          for (int j = i + 1; j < size; j++) {
            ItemKey kj = subset.get(j);
            long rj = kj.getResolvedItem();
            if (rj == 0)
              continue;
            if (key.equals(kj)) {
              // candidate -- check this artifact is not used yet
              if (!containsArtifact(result, rj)) {
                found = j;
                break;
              }
            }
          }
          if (found < 0) {
            result.add(key);
          } else {
            result.add(subset.get(found));
          }
        }
      }
    }
    return result;
  }

  private static boolean containsArtifact(List<ItemKey> result, long r) {
    boolean dupl = false;
    for (int j = 0; j < result.size(); j++) {
      if (r == result.get(j).getResolvedItem()) {
        dupl = true;
        break;
      }
    }
    return dupl;
  }

  @Nullable
  public ResolvedItem getMissingItem() {
    return myNotSetItem.get();
  }

  public RemoveableModifiable getModifiable() {
    return myModifiable;
  }

  @Nullable
  public BoolExpr<DP> createFilter(PropertyMap data, ItemHypercube hypercube) {
    List<ItemKey> subset = data.get(SUBSET);
    if (subset == null)
      return null;
    List<Long> items = resolve(subset, hypercube);
    BoolExpr<DP> filter = myKind.createFilter(items, myAttribute);
    ResolvedItem missing = getMissingItem();
    if (missing != null && items.contains(missing.getResolvedItem()))
      filter = filter.or(DPNotNull.create(myAttribute).negate());
    return filter;
  }

  public DBAttribute getAttribute() {
    return myAttribute;
  }

  @Nullable
  public Constraint createConstraint(PropertyMap data, ItemHypercube cube) {
    List<ItemKey> subset = data.get(SUBSET);
    if (subset == null)
      return null;
    List<Long> items = resolve(subset, cube);
    return myKind.createConstraint(items, myAttribute);
  }

  public boolean isSameData(PropertyMap data1, PropertyMap data2) {
    return Comparing.areSetsEqual(data1.get(SUBSET), data2.get(SUBSET));
  }

  public CanvasRenderable getPresentation() {
    return this;
  }

  @CanBlock
  public void waitForInitialization() throws InterruptedException {
    myItemKeys.waitForInitialization();
  }

  public PropertyMap getEditorData(PropertyMap data) {
    PropertyMap result = new PropertyMap();
    myModelKey.setInitialValue(result, data.get(SUBSET));
    return result;
  }

  public ConstraintEditor createEditor(ConstraintEditorNodeImpl node) {
    return new EnumAttributeConstraintEditor(getModelKey(), this, node, myOrder, myVariantsRenderer, mySearchSubstring);
  }

  public void writeFormula(FormulaWriter writer, PropertyMap data) {
    doWriteFormula(writer, getId(), data, myKind.getFormulaOperation());
  }

  public void writeFormula(FormulaWriter writer, String conditionId, PropertyMap data) {
    doWriteFormula(writer, conditionId, data, myKind.getFormulaOperation());
  }

  public int getEnumCount() {
    return getAllVariantsModel().getSize();
  }

  public AListModel<ItemKey> getEnumModel(final Lifespan life, ItemHypercube hypercube) {
    AListModel<? extends ResolvedItem> narrowed = myNarrower.narrowModel(life, myItemKeys.getModel(), hypercube);
    return ItemKeyModelCollector.createUnresolvedUniqueModel(life, narrowed);
  }

  @Override
  public List<ResolvedItem> getEnumList(ItemHypercube hypercube) {
    return myNarrower.narrowList((List<ResolvedItem>)myItemKeys.getModel().toList(), hypercube);
  }

  /**
   * @return sorted resolved enum model narrowed down to the hypercube. same-named items may occur
   */
  public <T extends ItemKey> AListModel<T> getResolvedEnumModel(Lifespan life, ItemHypercube hypercube) {
    AListModel<? extends ResolvedItem> narrowed = myNarrower.narrowModel(life, getAllVariantsModel(), hypercube);
    return (AListModel<T>)SortedListDecorator.create(life, narrowed, ResolvedItem.comparator());
  }

  public AListModel<ItemKey> getEnumFullModel() {
    return myItemKeys.getUnresolvedUniqueModel();
  }

  private AListModel<? extends ResolvedItem> getAllVariantsModel() {
    return myItemKeys.getModel();
  }

  public ConstraintDescriptor getDescriptor() {
    return this;
  }

  @Override
  public List<Long> resolveItem(String itemId, ItemHypercube cube) {
    List<ResolvedItem> keys = resolveKey(itemId, cube);
    List<Long> result = Collections15.arrayList();
    addItems(keys, result);
    return result;
  }

  @NotNull
  public List<ResolvedItem> resolveKey(@Nullable String itemId, ItemHypercube cube) {
    List<ResolvedItem> tempList = Collections15.arrayList();
    resolve(itemId, cube, tempList);
    return tempList;
  }

  public List<ResolvedItem> resolveKey(ItemKey key, @Nullable ItemHypercube cube) {
    return resolveKey(key.getId(), cube);
  }

  @Nullable
  public String suggestName(String descriptorName, PropertyMap data, Map<TypedKey<?>, ?> hints)
    throws CannotSuggestNameException
  {
    List<? extends ItemKey> enumSubset = data.get(SUBSET);
    if (enumSubset == null)
      return null;
    ResolvedItem missing = getMissingItem();
    if (enumSubset.isEmpty() && missing != null)
      enumSubset = Collections.singletonList(missing);
    if (enumSubset.isEmpty())
      return null;
    if (enumSubset.size() == 1)
      return enumSubset.get(0).getDisplayName();
    if (UserQueryNode.SINGLE_ENUM_PLEASE.getFrom(hints) == Boolean.TRUE)
      throw new CannotSuggestNameException();
    Integer maxLength = UserQueryNode.MAX_NAME_LENGTH.getFrom(hints);
    if (maxLength == null)
      maxLength = (int) Short.MAX_VALUE;
    StringBuffer buffer = new StringBuffer();
    for (ItemKey key : enumSubset) {
      String dispName = key.getDisplayName();
      if (buffer.length() == 0) {
        buffer.append(dispName);
      } else {
        if (buffer.length() + 2 + dispName.length() < maxLength) {
          buffer.append(", ");
          buffer.append(dispName);
        } else {
          throw new CannotSuggestNameException();
        }
      }
    }
    return buffer.toString();
  }

  public int compareTo(ConstraintDescriptor o) {
    return myId.compareTo(o.getId());
  }

  public List<Long> getComplementary(Collection<Long> values, ItemHypercube cube) {
    List<ResolvedItem> narrowed = getAllValues(cube);
    List<Long> result = Collections15.arrayList();
    for (ResolvedItem artifact : narrowed) {
      long resolved = artifact.getResolvedItem();
      if (!values.contains(resolved))
        result.add(resolved);
    }
    return result;
  }

  public List<ResolvedItem> getAllValues(ItemHypercube cube) {
    List<ResolvedItem> allValues = ThreadGate.AWT_IMMEDIATE.compute(new Computable<List<ResolvedItem>>() {
      public List<ResolvedItem> compute() {
        AListModel<? extends ResolvedItem> myEnum = getAllVariantsModel();
        List<ResolvedItem> result = Collections15.arrayList(myEnum.getSize());
        for (int i = 0; i < myEnum.getSize(); i++)
          result.add(myEnum.getAt(i));
        return result;
      }
    });
    return myNarrower.narrowList(allValues, cube);
  }


  public List<ResolvedItem> getAllValues(@NotNull Connection connection) {
    ItemHypercube cube = ItemHypercubeUtils.adjustForConnection(new ItemHypercubeImpl(), connection);
    return getAllValues(cube);
  }

  public void detach() {
    myLife.detach();
  }

  private static void doWriteFormula(FormulaWriter writer, String conditionId, PropertyMap values, String operation) {
    writer.addToken(conditionId);
    writer.addRaw(" " + operation + " (");
    List<ItemKey> subset = values.get(SUBSET);
    if (subset != null) {
      HashSet<String> writtenIds = Collections15.hashSet();
      for (ItemKey itemKey : subset) {
        String id = itemKey.getId();
        if (writtenIds.add(id)) {
          writer.addToken(id);
          writer.addRaw(" ");
        }
      }
    }
    writer.addRaw(")");
  }

  private PropertyKey<OrderListModel<ItemKey>, List<ItemKey>> getModelKey() {
    return myModelKey;
  }

  public static void register(TokenRegistry<FilterNode> registry, final String operation) {
    registry.registerInfixConstraint(operation, new InfixParser<FilterNode>() {
      public FilterNode parse(ParserContext<FilterNode> left, ParserContext<FilterNode> right) throws ParseException {
        ParserContext<FilterNode> context = right.stripBraces();
        List<String> artifactNames = context.getAllTokens();
        String id = left.getSingle();
        return ConstraintFilterNode.parsed(id, unresolved(id, operation),
          createValues(ItemKeys.itemId().collectList(artifactNames)));
      }
    });
  }

  protected static ConstraintType unresolved(final String id, final String operation) {
    return new ConstraintType() {
      @SuppressWarnings({"InnerClassFieldHidesOuterClassField"})
      private final SubsetKey myModelKey = new SubsetKey("subset", id);

      public ConstraintEditor createEditor(ConstraintEditorNodeImpl node) {
        //noinspection ConstantConditions
        return null;
      }

      public void writeFormula(FormulaWriter writer, String conditionId, PropertyMap data) {
        doWriteFormula(writer, conditionId, data, operation);
      }

      public PropertyMap getEditorData(PropertyMap data) {
        PropertyMap result = new PropertyMap();
        myModelKey.setInitialValue(result, data.get(SUBSET));
        return result;
      }

      @Nullable
      public String suggestName(String descriptorName, PropertyMap data, Map<TypedKey<?>, ?> hints)
        throws CannotSuggestNameException
      {
        throw new CannotSuggestNameException();
      }
    };
  }

  private static void addItems(List<ResolvedItem> tempResult, List<Long> result) {
    for (ResolvedItem resolvedItem : tempResult) {
      long resolved = resolvedItem.getResolvedItem();
      if (resolved > 0) result.add(resolved);
      else assert false : resolvedItem;
    }
  }

  @ThreadSafe
  private boolean resolve(@Nullable String artifactId, @Nullable ItemHypercube cube,
    Collection<? super ResolvedItem> result)
  {
    return resolveItemId(artifactId, cube, result, myNarrower, myItemKeys);
  }

  public static boolean resolveItemId(String itemId, ItemHypercube cube, Collection<? super ResolvedItem> result,
    EnumNarrower narrower, ItemKeyModelCollector<?> collector)
  {
    if (cube == null) cube = new ItemHypercubeImpl();
    Collection<Long> connections = ItemHypercubeUtils.getIncludedConnections(cube);
    boolean resolutionsFound = false;
    List<? extends ResolvedItem> candidates = collector.getAllResolvedListCopyOrNull(itemId);
    if (candidates != null && !candidates.isEmpty()) {
      for (ResolvedItem candidate : candidates) {
        long c = candidate.getConnectionItem();
        if (connections.isEmpty() || c == 0 || connections.contains(c)) {
          List<ResolvedItem> list = narrower.narrowList(Collections.singletonList(candidate), cube);
          result.addAll(list);
          if (list.size() > 0) {
            resolutionsFound = true;
          }
        }
      }
    }
    return resolutionsFound;
  }

  @NotNull
  public List<Long> resolve(Collection<ItemKey> keys, ItemHypercube cube) {
    List<Long> result = Collections15.arrayList();
    List<ResolvedItem> tempResult = Collections15.arrayList();
    for (ItemKey key : keys) {
      long artifact = key.getResolvedItem();
      if (artifact != 0)
        result.add(artifact);
      else {
        tempResult.clear();
        resolve(key.getId(), cube, tempResult);
        addItems(tempResult, result);
      }
    }
    return result;
  }

  public static ConstraintDescriptor unresolvedDescriptor(String id, String operation) {
    return ConstraintDescriptorProxy.stub(id, unresolved(id, operation));
  }

  public static FilterNode createNode(ConstraintDescriptor descriptor, ItemKey value) {
    return createNode(descriptor, Collections.<ItemKey>singletonList(value));
  }

  public static FilterNode createNode(ConstraintDescriptor descriptor, List<ItemKey> enums) {
    PropertyMap values = createValues(enums);
    return new ConstraintFilterNode(descriptor, values);
  }

  @Nullable
  public List<EnumGrouping> getAvailableGroupings() {
    return myGroupings;
  }

  @Override
  public Convertor<ItemKey, String> getFilterConvertor() {
    return myFilterConvertor;
  }

  @Nullable
  @ThreadAWT
  public ResolvedItem findForItem(long item) {
    Threads.assertAWTThread();
    if (item < 0) return null;
    AListModel<? extends ResolvedItem> model = myItemKeys.getModel();
    for (int i = model.getSize() - 1; i >= 0; i--) {
      ResolvedItem a = model.getAt(i);
      if (a.getResolvedItem() == item) {
        return a;
      }
    }
    return null;
  }

  public EnumConstraintKind getKind() {
    return myKind;
  }

  public static BaseEnumConstraintDescriptor createStarted(DBAttribute attribute, EnumNarrower narrower,
    String displayableName, ResolvedFactory<? extends ResolvedItem> keyFactory,
    @Nullable DBIdentifiedObject notSetArtifact, @Nullable BoolExpr<DP> variantFilter, EnumConstraintKind kind,
    @Nullable List<EnumGrouping> groupings, @Nullable Convertor<ItemKey, String> filterConvertor,
    @Nullable Comparator<? super ItemKey> order, @Nullable CanvasRenderer<ItemKey> variantsRenderer,
    boolean searchSubstring, DBItemType variantType, ItemKeyCache keyCache)
  {
    BoolExpr<DP> expr = calcExpr(variantFilter, variantType);
    final ItemKeyModelCollector<?> modelCollector =
      ItemKeyModelCollector.create(keyFactory, expr, "BECD:" + attribute.getId(), keyCache);
    BaseEnumConstraintDescriptor descriptor =
      createAndFetchNotSet(attribute, narrower, displayableName, notSetArtifact, kind, groupings, filterConvertor, order,
        variantsRenderer, searchSubstring, modelCollector);
    modelCollector.start(descriptor.myLife, Context.require(Database.ROLE));
    return descriptor;
  }

  public static BaseEnumConstraintDescriptor createAndFetchNotSet(DBAttribute attribute, EnumNarrower narrower,
    String displayableName, DBIdentifiedObject notSetArtifact, EnumConstraintKind kind, List<EnumGrouping> groupings,
    @Nullable Convertor<ItemKey, String> filterConvertor, Comparator<? super ItemKey> order,
    CanvasRenderer<ItemKey> variantsRenderer, boolean searchSubstring, ItemKeyModelCollector<?> modelCollector)
  {
    BaseEnumConstraintDescriptor descriptor =
      create(attribute, narrower, displayableName, notSetArtifact, kind, groupings, filterConvertor, order, variantsRenderer,
        searchSubstring, modelCollector);
    fetchNotSetItemLater(modelCollector.getKeyFactory(), descriptor.myNotSetObject, descriptor.myNotSetItem);
    return descriptor;
  }

  public static BaseEnumConstraintDescriptor create(DBAttribute attribute, EnumNarrower narrower,
    String displayableName, @Nullable DBIdentifiedObject notSetArtifact, EnumConstraintKind kind,
    @Nullable List<EnumGrouping> groupings, @Nullable Convertor<ItemKey, String> filterConvertor,
    @Nullable Comparator<? super ItemKey> order, @Nullable CanvasRenderer<ItemKey> variantsRenderer,
    boolean searchSubstring, ItemKeyModelCollector<?> modelCollector)
  {
    return new BaseEnumConstraintDescriptor(attribute, narrower, displayableName, notSetArtifact, kind, groupings,
      filterConvertor, order, variantsRenderer, searchSubstring, attribute.getName(), modelCollector);
  }

  public static BoolExpr<DP> calcExpr(BoolExpr<DP> variantFilter, DBItemType variantType) {
    BoolExpr<DP> expr;
    if(variantType != null) {
      expr = DPEqualsIdentified.create(DBAttribute.TYPE, variantType);
      if(variantFilter != null) {
        expr = expr.and(variantFilter);
      }
    } else if(variantFilter != null) {
      expr = variantFilter;
    } else {
      throw new IllegalArgumentException();
    }
    return expr;
  }

  private static void fetchNotSetItemLater(final ResolvedFactory<? extends ResolvedItem> factory,
    final DBIdentifiedObject notSetObject, final Synchronized<ResolvedItem> target) {
    if (notSetObject != null) {
      Database.require().writeForeground(new WriteTransaction<ResolvedItem>() {
        public ResolvedItem transaction(DBWriter writer) {
          long a = writer.materialize(notSetObject);
          try {
            return factory.createResolvedItem(a, writer);
          } catch (BadItemException e) {
            return null;
          }
        }
      }).finallyDo(ThreadGate.AWT, new Procedure<ResolvedItem>() {
        public void invoke(ResolvedItem arg) {
          target.set(arg);
        }
      });
    }
  }

  public static class SubsetKey extends PropertyKey<OrderListModel<ItemKey>, List<ItemKey>> {
    private final String myId;

    public SubsetKey(String name, String id) {
      super(name, new TypedKeyWithEquality<List<ItemKey>>(name, id));
      myId = id;
    }

    public boolean equals(Object obj) {
      return obj instanceof SubsetKey && myId.equals(((SubsetKey) obj).myId);
    }

    public List<ItemKey> getModelValue(PropertyModelMap properties) {
      OrderListModel<ItemKey> listModel = properties.get(this);
      assert listModel != null : this;
      return listModel.toList();
    }

    public int hashCode() {
      return myId.hashCode();
    }

    public void installModel(final ChangeSupport changeSupport, PropertyModelMap propertyMap) {
      OrderListModel<ItemKey> model = OrderListModel.create();
      propertyMap.put(this, model);
      model.addListener(new AListModel.Adapter() {
        public void onChange() {
          changeSupport.fireChanged(getValueKey(), null, null);
        }
      });
    }

    public ChangeState isChanged(PropertyModelMap models, PropertyMap originalValues) {
      return ChangeState.choose(this, originalValues, getModelValue(models));
    }

    public void setInitialValue(PropertyMap values, List<ItemKey> value) {
      values.put(getValueKey(), value);
    }

    public void setModelValue(PropertyModelMap properties, List<ItemKey> value) {
      OrderListModel<ItemKey> model = properties.get(this);
      assert model != null : this;
      model.clear();
      model.addAll(Collections15.arrayList(value));
    }
  }
}
