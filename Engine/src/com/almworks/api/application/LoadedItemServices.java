package com.almworks.api.application;

import com.almworks.api.application.qb.EnumConstraintType;
import com.almworks.api.application.util.PredefinedKey;
import com.almworks.api.engine.Connection;
import com.almworks.api.engine.Engine;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.integers.LongList;
import com.almworks.util.properties.Role;
import org.jetbrains.annotations.*;

public interface LoadedItemServices {
  ModelKey<LoadedItemServices> VALUE_KEY = PredefinedKey.create("#loadedItemServices");

  Engine getEngine();

  @NotNull
  Connection requireConnection() throws ConnectionlessItemException;

  @Nullable
  Connection getConnection();

  @Nullable
  <C extends Connection> C getConnection(Class<C> c);

  ItemHypercube getConnectionCube();

  MetaInfo getMetaInfo();

  <T> T getService(Class<T> aClass);

  <T> T getActor(Role<T> role);

  ItemKeyCache getItemKeyCache();

  VariantsModelFactory getVariantsFactory(EnumConstraintType type);

  long getItem();

  @Nullable
  String getItemUrl();

  boolean isDeleted();

  boolean hasProblems();

  @NotNull
  LongList getEditableSlaves();

  boolean isLockedForUpload();
}
