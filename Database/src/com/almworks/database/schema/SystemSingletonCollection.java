package com.almworks.database.schema;

import com.almworks.api.database.typed.TypedArtifact;
import com.almworks.api.database.util.*;
import com.almworks.database.Basis;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;

import java.util.Map;

public class SystemSingletonCollection extends SingletonCollection {
  protected final Basis myBasis;
  private final Map<String, SystemSingleton> mySystemSingletons = Collections15.hashMap();

  public SystemSingletonCollection(Basis basis) {
    myBasis = basis;
  }

  protected <T extends TypedArtifact> Singleton<T> singleton(TypedKey<T> key, Initializer initializer) {
    return singleton(key.getName(), initializer);
  }

  public <T extends TypedArtifact> Singleton<T> singleton(String ID, Initializer initializer) {
    SystemSingleton<T> singleton = new SystemSingleton<T>(myBasis, ID, initializer);
    addSingleton(singleton);
    SystemSingleton prev = mySystemSingletons.put(ID, singleton);
    assert prev == null;
    return singleton;
  }

  public <T extends TypedArtifact> SystemSingleton<T> search(TypedKey<T> key) {
    SystemSingleton singleton = mySystemSingletons.get(key.getName());
    if (singleton != null)
      return singleton;
    SingletonCollection[] chained = getChained();
    for (int i = 0; i < chained.length; i++) {
      SingletonCollection collection = chained[i];
      if (collection instanceof SystemSingletonCollection) {
        singleton = ((SystemSingletonCollection) collection).search(key);
        if (singleton != null)
          return singleton;
      }
    }
    return null;
  }
}
