package com.almworks.api.application;

import com.almworks.api.engine.Connection;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.util.properties.Role;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.Set;

/**
 * @author : Dyoma
 */
// todo :refactoring: don't see a method? use getCreator().whateverYouNeed()
public interface UserChanges {
  <T> T getNewValue(ModelKey<T> key);

  <T> T getNewValue(TypedKey<T> key);

  ItemVersionCreator getCreator();

  DBReader getReader();

  // todo :refactoring: if your key uses Set<Long>, don't try hack this method by using "? extends Collection<Long>", write another method
  void resolveArrayValue(AttributeModelKey<Collection<ItemKey>,Set<Long>> modelKey);

  // call from DB thread
  void invalidValue(ModelKey<?> key, String value);

  // call from DB thread
  void addProblem(String text);

  long getConnectionItem();

  @Nullable
  Connection getConnection();

  ItemHypercube getContextHypercube();

  <T> T getActor(Role<T> role);

  ItemHypercubeImpl createConnectionCube();
}