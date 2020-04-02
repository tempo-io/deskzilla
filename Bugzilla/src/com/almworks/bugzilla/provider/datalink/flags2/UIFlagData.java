package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.api.application.*;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.application.qb.EnumGrouping;
import com.almworks.api.engine.*;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.api.syncreg.ItemHypercubeUtils;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.flags2.columns.FlagColumns;
import com.almworks.bugzilla.provider.datalink.schema.*;
import com.almworks.bugzilla.provider.qb.flags.FlagConstraintDescriptor;
import com.almworks.explorer.qbuilder.filter.*;
import com.almworks.items.api.*;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.collections.LongSet;
import com.almworks.util.models.TableColumnAccessor;
import org.almworks.util.*;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.*;

public class UIFlagData implements ResolvedFactory<FlagTypeItem> {
  private final ItemKeyModelCollector<FlagTypeItem> myTypes;
  private final CommonMetadata myMd;
  private final FlagColumns myColumns;
  private final FlagTypeNarrower myStrong;
  private final FlagTypeNarrower myWeak;
  private final FlagTypeNarrower myDefault;

  public UIFlagData(CommonMetadata md, ItemKeyCache cache) {
    myTypes = ItemKeyModelCollector.create(this, Flags.KIND_TYPE, "flagTypes", cache);
    myMd = md;
    myStrong = new FlagTypeNarrower(Boolean.FALSE, myMd);
    myWeak = new FlagTypeNarrower(Boolean.TRUE, myMd);
    myDefault = new FlagTypeNarrower(null, myMd);
    myColumns = new FlagColumns(this);
  }

  public void start(Database db) {
    myTypes.start(Lifespan.FOREVER, db);
  }

  @Nullable
  public String getTypeName(long type) {
    FlagTypeItem typeKey = getTypeKey(type);
    return typeKey != null ? typeKey.getDisplayName() : null;
  }

  public ItemKey getUserKey(long userItem) {
    return User.ENUM_USERS.getKeyCollector(myMd).findForItem(userItem);
  }

  public String getUserName(long userItem) {
    ItemKey user = getUserKey(userItem);
    return user != null ? user.getDisplayName() : "";
  }

  public String getTypeDescription(long type) {
    FlagTypeItem typeKey = getTypeKey(type);
    return typeKey != null ? typeKey.getDescription() : null;
  }

  public FlagTypeItem getTypeKey(long type) {
    return myTypes.findForItem(type);
  }

  public BugzillaConnection getConnection(Long connection) {
    if (connection == null) return null;
    return Util.castNullable(BugzillaConnection.class, myMd.getActor(Engine.ROLE).getConnectionManager().findByItem(connection));
  }

  public EnumNarrower getDefaultNarrower() {
    return myDefault;
  }

  @Override
  public FlagTypeItem createResolvedItem(long item, DBReader reader) throws BadItemException {
    LoadedFlagType loaded = LoadedFlagType.load(SyncUtils.readTrunk(reader, item));
    if (loaded == null) return null;
    return FlagTypeItem.create(loaded, this);
  }

  public AListModel<? extends TableColumnAccessor<LoadedItem, ?>> getColumnsForConnection(Lifespan life, long connection) {
    return myColumns.getColumnsForConnection(life, connection);
  }

  public AListModel<FlagTypeItem> getTypesModel() {
    return myTypes.getModel();
  }

  public static Comparator<? super TableColumnAccessor<LoadedItem, ?>> getColumnsComparator() {
    return FlagColumns.COLUMNS_COMPARATOR;
  }

  public List<ConstraintDescriptor> createConstraintDecriptors(CommonMetadata md) {
    ItemKeyModelCollector<ResolvedItem> userCollector = User.ENUM_USERS.getKeyCollector(md);
    final BaseEnumConstraintDescriptor setter =
      createUserEnum(Flags.CACHE_SETTERS, "Flag Setter", User.GROUPING, userCollector);
    final BaseEnumConstraintDescriptor requestee =
      createUserEnum(Flags.CACHE_REQUESTEES, "Flag Requestee", User.GROUPING, userCollector);
    final BaseEnumConstraintDescriptor flagType =
      createTypeEnum(Flags.CACHE_TYPES, "Flag Type", myTypes, myDefault);

    final FlagConstraintDescriptor compound = new FlagConstraintDescriptor(flagType, setter, requestee, User.userResolver);

    return Collections15.<ConstraintDescriptor>arrayList(setter, requestee, flagType, compound);
  }

  private static BaseEnumConstraintDescriptor createUserEnum(
    DBAttribute<Set<Long>> attribute, String displayableName,
    List<EnumGrouping> userGroupping, ItemKeyModelCollector<?> userCollector)
  {
    return BaseEnumConstraintDescriptor.create(
      attribute, EnumNarrower.DEFAULT, displayableName, null,
      EnumConstraintKind.INTERSECTION, userGroupping, ItemKey.GET_ID, null, User.RENDERER, true, userCollector);
  }

  private static BaseEnumConstraintDescriptor createTypeEnum(
    DBAttribute<Set<Long>> attribute, String displayableName, ItemKeyModelCollector<FlagTypeItem> types, EnumNarrower narrower)
  {
    return BaseEnumConstraintDescriptor.create(
      attribute, narrower, displayableName, null,
      EnumConstraintKind.INTERSECTION, null, null, null, null, true, types);
  }

  public static UIFlagData getInstance(ItemWrapper uiItem) {
    BugzillaConnection connection = BugzillaConnection.getInstance(uiItem);
    if (connection == null) return null;
    return connection.getCommonMD().myFlags;
  }

  public void ensureTypeKnown(DBReader reader, long type) throws BadItemException {
    if (type <= 0) {
      Log.error("Illegal type " + type);
      return;
    }
    myTypes.ensureKnown(reader, type);
  }

  public void ensureUserKnown(DBReader reader, long user) throws BadItemException {
    if (user <= 0) return;
    User.ENUM_USERS.getKeyCollector(myMd).ensureKnown(reader, user);
  }

  private static class FlagTypeNarrower extends EnumNarrower.Filtering<Pair<long[], LongSet>> {
    private static final Pair<long[],LongSet> ALL = Pair.create(Const.EMPTY_LONGS, new LongSet());
    private static final Pair<long[],LongSet> NONE = Pair.create(null, null);
    private final Boolean myWeak;
    private final CommonMetadata myMd;

    private FlagTypeNarrower(Boolean weak, CommonMetadata md) {
      myWeak = weak;
      myMd = md;
    }

    @Override
    protected Pair<long[], LongSet> getNarrowDownData(ItemHypercube cube) {
      long[] connectionKeys;
      Collection<Long> connectionArtifacts = ItemHypercubeUtils.getIncludedConnections(cube);
      List<BugzillaConnection> connections = Collections15.arrayList();
      if (connectionArtifacts == null || connectionArtifacts.isEmpty()) return ALL;
      else {
        LongSet set = new LongSet();
        Engine engine = CommonMetadata.getContainer().getActor(Engine.ROLE);
        if(engine == null) return NONE;
        ConnectionManager manager = engine.getConnectionManager();
        for (Long pointer : connectionArtifacts) {
          Connection connection = manager.findByItem(pointer);
          if (!(connection instanceof BugzillaConnection)) continue;
          set.add(pointer);
          connections.add((BugzillaConnection) connection);
        }
        connectionKeys = set.toNativeArray();
      }
      if (connections.isEmpty()) return NONE;
      LongSet components = new LongSet();
      SortedSet<Long> c = cube.getIncludedValues(SingleEnumAttribute.COMPONENT.getBugAttribute());
      if (c != null)
        for (Long pointer : c) components.add(pointer);
      else {
        SortedSet<Long> p = cube.getIncludedValues(Bug.attrProduct);
        if (p != null && !p.isEmpty()) {
          ItemKeyModelCollector<ResolvedItem> products = Product.ENUM_PRODUCTS.getKeyCollector(myMd);
          for (Long pPointer : p) {
            ResolvedItem product = products.findForItem(pPointer);
            if(product == null) continue;
            BugzillaConnection connection = product.getConnection(BugzillaConnection.class);
            if (connection == null) continue;
            ProductDependencyInfo info = connection.getDependenciesTracker().getInfo(product);
            if (info == null) continue;
            Set<? extends ItemKey> compKeys = info.getValidValues(BugzillaAttribute.COMPONENT);
            for (ItemKey key : compKeys) {
              long compItem = key.getResolvedItem();
              if (compItem > 0) components.add(compItem);
            }
          }
        }
      }
      return Pair.create(connectionKeys, components);
    }

    @Override
    protected boolean isAccepted(ResolvedItem artifact, @NotNull Pair<long[], LongSet> data, ItemHypercube cube) {
      if (data == ALL) return true;
      if (data == NONE) return false;
      if (!(artifact instanceof FlagTypeItem)) return false;
      long[] connections = data.getFirst();
      LongSet components = data.getSecond();
      FlagTypeItem type = (FlagTypeItem) artifact;
      if (connections.length > 0 && ArrayUtil.indexOf(connections, type.getConnectionItem()) < 0) return false;
      if (components == null || components.size() == 0) return true;
      if (inapplicableInAllComponents(type, components)) return false;
      boolean weak = myWeak == null ? true : myWeak;
      return weak || type.intersectsComponents(components, true);
    }

    private static boolean inapplicableInAllComponents(FlagTypeItem type, LongSet components) {
      return type.containsComponents(components, false);
    }
  }
}
