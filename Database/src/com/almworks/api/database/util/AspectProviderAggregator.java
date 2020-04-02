package com.almworks.api.database.util;

import com.almworks.api.database.AspectProvider;
import com.almworks.api.database.Aspected;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class AspectProviderAggregator implements AspectProvider {
  private final List<AspectProvider> myAspectProviders = Collections15.arrayList();

  public void addProvider(AspectProvider provider) {
    assert provider != null;
    myAspectProviders.add(provider);
  }

  public <K> K getAspect(Aspected object, TypedKey<K> key) {
    for (Iterator<AspectProvider> iterator = myAspectProviders.iterator(); iterator.hasNext();) {
      AspectProvider aspectProvider = iterator.next();
      final K aspect = aspectProvider.getAspect(object, key);
      if (aspect != null)
        return aspect;
    }
    return null;
  }

  public void copyAspects(Aspected object, Map<TypedKey, ?> receptacle) {
    for (Iterator<AspectProvider> iterator = myAspectProviders.iterator(); iterator.hasNext();) {
      AspectProvider aspectProvider = iterator.next();
      aspectProvider.copyAspects(object, receptacle);
    }
  }
}
