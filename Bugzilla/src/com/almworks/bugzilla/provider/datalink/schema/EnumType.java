package com.almworks.bugzilla.provider.datalink.schema;

import com.almworks.api.application.*;
import com.almworks.api.application.qb.EnumGrouping;
import com.almworks.api.explorer.gui.TextResolver;
import com.almworks.api.explorer.util.UIControllerUtil;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.BugzillaProvider;
import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.bugzilla.provider.datalink.DefaultReferenceLink;
import com.almworks.explorer.qbuilder.filter.*;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.util.DBNamespace;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.FilteringListDecorator;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.Convertor;
import com.almworks.util.collections.Functional;
import com.almworks.util.commons.Function;
import com.almworks.util.components.CanvasRenderer;
import org.almworks.util.*;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.List;

public class EnumType {
  private final DBAttribute<String> myNameAttr;
  private final DBAttribute<String> myUniqueIdAttr;
  private final String myDefaultValue;
  private final DBItemType myType;
  private final ResolvedFactory<ResolvedItem> myFactory;
  private final TypedKey<ItemKeyModelCollector<ResolvedItem>> myCollectorKey;
  @Nullable
  private final List<EnumGrouping> myGrouping;
  private final Convertor<ItemKey,String> myFilterConvertor;
  private final String myDebugName;
  @Nullable
  private final TextResolver myResolver;

  private static final DBNamespace NS = BugzillaProvider.NS.subNs("enumType");
  /** Set on type item, points to the bug attribute for this enum type field. */
  public static final DBAttribute<Long> BUG_ATTR = NS.link("bugAttr");
  /** Set on type item, points to the attribute for enum value's ID string. */
  public static final DBAttribute<Long> ID_ATTR = NS.link("idAttr");

  public EnumType(
    DBItemType type, DBAttribute<String> nameAttr, DBAttribute<String> uniqueIdAttr,
    List<EnumGrouping> grouping, Convertor<ItemKey,String> filterConvertor,
    String debugName, String defaultValue, TextResolver resolver, ResolvedFactory<ResolvedItem> customFactory)
  {
    myType = type;
    myNameAttr = nameAttr;
    myUniqueIdAttr = uniqueIdAttr;
    myDefaultValue = defaultValue;
    myGrouping = grouping != null ? Collections15.unmodifiableListCopy(grouping) : null;
    myFilterConvertor = filterConvertor;
    myDebugName = debugName;
    myResolver = resolver;
    myFactory = customFactory != null ? customFactory : new ResolvedFactory.DefaultResolvedFactory(myNameAttr, myUniqueIdAttr);
    myCollectorKey = TypedKey.create(debugName);
  }

  public static EnumType create(DBNamespace ns, @Nullable List<EnumGrouping> grouping,
    @Nullable Convertor<ItemKey, String> filterConvertor, String debugName, String defaultValue)
  {
    DBAttribute<String> nameAttr = ns.string("visualKey", "Visual Key", false);
    DBAttribute<String> keyAttr = ns.string("key", "Key", false);
    DBItemType type = ns.type();
    type.initialize(ID_ATTR, keyAttr);
    return new EnumType(type, nameAttr, keyAttr, grouping, filterConvertor, debugName, defaultValue, null, null);
  }

  public DefaultReferenceLink createDefaultRefLink(DBAttribute<Long> bugAttribute, BugzillaAttribute bzAttribute, boolean inProto, boolean checkUpdate) {
    return new DefaultReferenceLink(bugAttribute, bzAttribute, myType, myUniqueIdAttr, myNameAttr,
      false, myDefaultValue, inProto, checkUpdate);
  }

  public final DBItemType getType() {
    return myType;
  }

  public final ResolvedFactory<ResolvedItem> getFactory() {
    return myFactory;
  }

  public final String getDefaultValue() {
    return myDefaultValue;
  }

  @Nullable
  public final TextResolver getResolver() {
    return myResolver;
  }

  public String getStringId(ItemVersion value) {
    return value.getValue(myUniqueIdAttr);
  }

  public final ItemKeyModelCollector<ResolvedItem> getKeyCollector(CommonMetadata md) {
    BoolExpr<DP> filter = DPEqualsIdentified.create(DBAttribute.TYPE, myType);
    return createKeyCollector(md, filter, myCollectorKey, myFactory, myDebugName);
  }

  public static <R extends ResolvedItem> ItemKeyModelCollector<R> createKeyCollector(CommonMetadata md, BoolExpr<DP> filter,
    TypedKey<ItemKeyModelCollector<R>> collectorKey, ResolvedFactory<R> factory, String debugName) {
    ItemKeyModelCollector<R> collector = md.getMetaService(collectorKey);
    if (collector != null) return collector;
    ItemKeyModelCollector<R> created = ItemKeyModelCollector.create(factory, filter, debugName, md.getActor(NameResolver.ROLE).getCache());
    collector = md.installOrGetService(collectorKey, created);
    if (collector == created) created.start(Lifespan.FOREVER, md.getDatabase());
    if (collector == null) Log.error("Failed to register collector");
    return collector;
  }

  private BaseEnumConstraintDescriptor createDescriptor(CommonMetadata md, DBAttribute<?> bugAttribute,
    EnumNarrower narrower, String displayName, CanvasRenderer<ItemKey> valueRenderer)
  {
    if (bugAttribute.getScalarClass() != Long.class) {
      Log.error("Wrong scalar class " + bugAttribute);
      return null;
    }
    EnumConstraintKind kind;
    if (bugAttribute.getComposition() == DBAttribute.ScalarComposition.SCALAR) kind = EnumConstraintKind.INCLUSION;
    else kind = EnumConstraintKind.INTERSECTION;
    final ItemKeyModelCollector<?> modelCollector = getKeyCollector(md);
    return BaseEnumConstraintDescriptor.createAndFetchNotSet(bugAttribute, narrower,
      displayName, null, kind, myGrouping, myFilterConvertor, null, valueRenderer, false, modelCollector);
  }

  public BaseEnumConstraintDescriptor singleValueDescriptor(CommonMetadata md, DBAttribute<Long> bugAttribute,
    EnumNarrower narrower, String displayName, CanvasRenderer<ItemKey> valueRenderer) {
    if (bugAttribute.getComposition() != DBAttribute.ScalarComposition.SCALAR) {
      Log.error("Wrong attribute composition " + bugAttribute);
      return null;
    }
    return createDescriptor(md, bugAttribute, narrower, displayName, valueRenderer);
  }

  public BaseEnumConstraintDescriptor multiValueDescriptor(CommonMetadata md,
    DBAttribute<? extends Collection<? extends Long>> bugAttribute, EnumNarrower narrower, String displayName,
    CanvasRenderer<ItemKey> valueRenderer)
  {
    DBAttribute.ScalarComposition composition = bugAttribute.getComposition();
    if (composition != DBAttribute.ScalarComposition.LIST && composition != DBAttribute.ScalarComposition.SET) {
      Log.error("Wrong attribute composition " + bugAttribute);
      return null;
    }
    return createDescriptor(md, bugAttribute, narrower, displayName, valueRenderer);
  }

  private DBQuery findItems(DBReader reader, ItemReference connection) {
    BoolExpr<DP> expr =
      DPEqualsIdentified.create(SyncAttributes.CONNECTION, connection).and(DPEqualsIdentified.create(DBAttribute.TYPE, myType));
    return reader.query(expr);
  }

  public long findOrCreate(UserChanges changes, String enumId) {
    if (myResolver != null)
      try {
        return myResolver.resolve(enumId, changes);
      } catch (BadItemKeyException e) {
        Log.error("Can not resolve " + enumId);
        return 0;
      }
    long item = findById(changes.getReader(), new ItemReference.Item(changes.getConnectionItem()), enumId);
    if (item <= 0) Log.error("No resolution found");
    return item;
  }

  private static final Function<Iterable<? extends ResolvedItem>, List<ResolvedItem>> enabledFilter =
    Functional.<ResolvedItem>filterToList(UIControllerUtil.IS_ENABLED);

  public VariantsModelFactory getVariantsFactory(CommonMetadata md, final ItemHypercube cube, final EnumNarrower narrower) {
    final ItemKeyModelCollector<ResolvedItem> collector = getKeyCollector(md);
    return new VariantsModelFactory() {
      public AListModel<ItemKey> createModel(Lifespan life) {
        return getEnumModel(life, cube);
      }

      private AListModel<ItemKey> getEnumModel(final Lifespan life, ItemHypercube hypercube) {
        AListModel<ResolvedItem> narrowed =
          (AListModel<ResolvedItem>)narrower.narrowModel(life, collector.getModel(), hypercube);
        FilteringListDecorator<ResolvedItem> enabled =
          FilteringListDecorator.create(life, narrowed, UIControllerUtil.IS_ENABLED);
        return ItemKeyModelCollector.createUnresolvedUniqueModel(life, enabled);
      }

      @NotNull
      @Override
      public List<ResolvedItem> getResolvedList() {
        return enabledFilter.invoke(narrower.narrowList(collector.getModel().toList(), cube));
      }
    };
  }

  public long findById(DBReader reader, ItemReference connectionItem, String enumId) {
    return findItems(reader, connectionItem).getItemByKey(myUniqueIdAttr, enumId);
  }
}
