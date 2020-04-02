package com.almworks.database;

import com.almworks.api.database.*;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class AspectManagerImpl implements AspectManager {
  private final Map<Class, List<AspectProvider>> myProviders = Collections15.hashMap();

  public <T> void registerAspectProvider(Class<T> aspectedClass, AspectProvider provider) {
    assert aspectedClass != null;
    assert provider != null;
    synchronized (myProviders) {
      List<AspectProvider> list = myProviders.get(aspectedClass);
      if (list == null) {
        list = Collections15.arrayList();
        myProviders.put(aspectedClass, list);
      }
      list.add(provider);
    }
  }

  public <K> K getAspect(Aspected aspected, TypedKey<K> key) {
    AspectProvider[] providers = getProviders(aspected.getClass());
    if (providers == null)
      return null;
    for (int i = 0; i < providers.length; i++) {
      K aspect = providers[i].getAspect(aspected, key);
      if (aspect != null)
        return aspect;
    }
    return null;
  }

  private AspectProvider[] getProviders(Class aspectedClass) {
    List<AspectProvider> providers = null;
    synchronized (myProviders) {
      providers = myProviders.get(aspectedClass);
      if (providers == null) {
        for (Iterator<Class> iterator = myProviders.keySet().iterator(); iterator.hasNext();) {
          Class possibleClass = iterator.next();
          if (possibleClass.isAssignableFrom(aspectedClass)) {
            providers = myProviders.get(possibleClass);
            break;
          }
        }
      }
      return providers == null ? null : providers.toArray(new AspectProvider[providers.size()]);
    }
  }

  public Map<TypedKey, ?> copyAspects(Aspected aspected) {
    AspectProvider[] providers = getProviders(aspected.getClass());
    if (providers == null)
      return Collections15.emptyMap();
    Map<TypedKey, ?> result = Collections15.hashMap();
    for (int i = 0; i < providers.length; i++) {
      providers[i].copyAspects(aspected, result);
    }
    return result;
  }
}
