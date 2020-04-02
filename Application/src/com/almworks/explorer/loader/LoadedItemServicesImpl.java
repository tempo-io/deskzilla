package com.almworks.explorer.loader;

import com.almworks.api.application.*;
import com.almworks.api.application.qb.EnumConstraintType;
import com.almworks.api.engine.Connection;
import com.almworks.api.engine.Engine;
import com.almworks.api.syncreg.*;
import com.almworks.integers.LongList;
import com.almworks.integers.LongListIterator;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.SyncManager;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.properties.Role;
import com.almworks.util.threads.Threads;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.List;

import static com.almworks.util.collections.Functional.isEmpty;

/**
 * @author : Dyoma
 */
class LoadedItemServicesImpl implements LoadedItemServices {
  private final long myItem;
  private final ItemModelRegistryImpl myRegistry;
  private final Connection myConnection;
  private final MetaInfo myMetaInfo;
  @Nullable
  private final String myItemUrl;
  private final ItemHypercube myCube;
  @Nullable
  private NameResolver myResolver;
  private final boolean myDeleted;
  private final LongList myEditableSlaves;

  public LoadedItemServicesImpl(ItemModelRegistryImpl registry, ItemVersion itemVersion, boolean deleted, Connection connection, LongList editableSlaves, @NotNull MetaInfo metaInfo) {
    if (metaInfo == null) {
      Log.error("LISI:null MI");
      assert false;
    }
    myEditableSlaves = editableSlaves;
    Threads.assertLongOperationsAllowed();
    myRegistry = registry;
    myItem = itemVersion.getItem();
    myConnection = connection;
    myMetaInfo = metaInfo;
    myItemUrl = myConnection == null ? null : myConnection.getItemUrl(itemVersion);
    myCube = ItemHypercubeUtils.adjustForConnection(new ItemHypercubeImpl(), myConnection);
    myDeleted = deleted;
  }

  /** For unknown types (types for which we don't have meta info in the registry), returns null. */
  @Nullable
  public static LoadedItemServicesImpl create(ItemModelRegistryImpl registry, ItemVersion version) {
    boolean itemInvisible = Boolean.TRUE.equals(version.getValue(SyncAttributes.INVISIBLE));
    boolean itemRemoved = SyncUtils.isRemoved(version);
    Connection connection = extractConnectionOrNull(registry.getEngine(), version);
    if (connection == null) return null;
    LongList editableSlaves = connection.getProvider().getPrimaryStructure().loadEditableSlaves(version);
    MetaInfo metaInfo = MetaInfo.REGISTRY.getMetaInfo(version.getItem(), version.getReader());
    return metaInfo == null ? null : new LoadedItemServicesImpl(registry, version, itemInvisible || itemRemoved, connection, editableSlaves, metaInfo);
  }

  @NotNull
  @Override
  public LongList getEditableSlaves() {
    return myEditableSlaves;
  }

  @Override
  public boolean isLockedForUpload() {
    SyncManager syncMan = getActor(SyncManager.ROLE);
    boolean isLockedForUpload = !syncMan.canUpload(getItem());
    for (LongListIterator i = getEditableSlaves().iterator(); i.hasNext();) {
      isLockedForUpload |= !syncMan.canUpload(i.next());
      if (isLockedForUpload) break;
    }
    return isLockedForUpload;
  }

  public VariantsModelFactory getVariantsFactory(final EnumConstraintType type) {
    return new VariantsModelFactory() {
      public AListModel<ItemKey> createModel(Lifespan life) {
        return type.getEnumModel(life, myCube);
      }

      @NotNull
      @Override
      public List<ResolvedItem> getResolvedList() {
        return type.getEnumList(myCube);
      }
    };
  }

  @Override
  public ItemKeyCache getItemKeyCache() {
    return getResolver().getCache();
  }

  @NotNull
  private NameResolver getResolver() {
    NameResolver resolver = myResolver;
    if (resolver != null)
      return resolver;
    myResolver = myRegistry.getActor(NameResolver.ROLE);
    assert myResolver != null;
    return myResolver;
  }

  @Override
  public long getItem() {
    return myItem;
  }

  @NotNull
  public Connection requireConnection() throws ConnectionlessItemException {
    if (myConnection == null)
      throw new ConnectionlessItemException();
    return myConnection;
  }

  @Override
  public Connection getConnection() {
    return myConnection;
  }

  @Override
  public <C extends Connection> C getConnection(Class<C> c) {
    if (myConnection != null && !c.isAssignableFrom(myConnection.getClass())) {
      Log.warn("Wrong connection class " + myConnection.getClass() + " " + c, new Throwable());
      return null;
    }
    return (C) myConnection;
  }

  public boolean hasProblems() {
    return !isEmpty(myRegistry.getActor(Engine.ROLE).getSynchronizer().getItemProblems(myItem));
  }

  public MetaInfo getMetaInfo() {
    return myMetaInfo;
  }

  @Nullable
  public String getItemUrl() {
    return myItemUrl;
  }

  @Override
  public boolean isDeleted() {
    return myDeleted;
  }

  @Nullable
  static Connection extractConnectionOrNull(Engine engine, ItemVersion itemVersion) {
    Long ca = itemVersion.getValue(SyncAttributes.CONNECTION);
    if (ca == null) {
      Log.warn("Missing connection " + itemVersion.getItem(), new Throwable());
      return null;
    }
    assert engine != null : itemVersion.getItem();
    Connection connection = engine.getConnectionManager().findByItem(ca);
    if (connection == null) Log.warn("Unknown connection " + ca, new Throwable());
    return connection;
  }

  /**
   * Replaced with {@link #getActor(com.almworks.util.properties.Role<T>)}
   */
  @Deprecated
  public <T> T getService(Class<T> aClass) {
    return myRegistry.getService(aClass);
  }

  public Engine getEngine() {
    return myRegistry.getEngine();
  }

  public ItemHypercube getConnectionCube() {
    return myCube;
  }

  public <T> T getActor(Role<T> role) {
    return myRegistry.getActor(role);
  }

  public String toString() {
    return "lisi[" + myItem + "]";
  }
}