package com.almworks.api.database.util;

import com.almworks.api.database.AspectProvider;
import com.almworks.api.database.Aspected;
import org.almworks.util.*;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public abstract class AspectProviderBase <D> implements AspectProvider {
  private final List<Aspect> myAspects = Collections15.arrayList();

  public final <K> K getAspect(Aspected object, TypedKey<K> key) {
    assert key != null;
    D aspectedObject = getAspectedObject(object);
    if (aspectedObject == null)
      return null;
    for (Iterator<Aspect> iterator = myAspects.iterator(); iterator.hasNext();) {
      Aspect aspect = iterator.next();
      if (key.equals(aspect.getKey()))
        return key.cast(aspect.getAspect(aspectedObject));
    }
    return null;
  }

  public final void copyAspects(Aspected object, Map<TypedKey, ?> receptacle) {
    D aspectedObject = getAspectedObject(object);
    if (aspectedObject == null)
      return;
    for (Iterator<Aspect> iterator = myAspects.iterator(); iterator.hasNext();) {
      Aspect aspect = iterator.next();
      Object aspectValue = aspect.getAspect(aspectedObject);
      if (aspectValue != null)
        putAspect(aspectedObject, receptacle, aspect.getKey(), aspectValue);
    }
  }

  protected final void addAspect(Aspect<D, ?> aspect) {
    myAspects.add(aspect);
  }

  protected abstract D getAspectedObject(Aspected object);

  private <T> void putAspect(D aspectedObject, Map<TypedKey, ?> receptacle, TypedKey<T> aspectKey, T aspect) {
    if (aspect == null)
      return;
    if (aspectKey.getFrom(receptacle) != null) {
      Log.warn("aspect collision over " + aspectKey + " on " + aspectedObject);
    }
    aspectKey.putTo(receptacle, aspect);
  }

  public static abstract class Aspect <D, T> {
    private final TypedKey<T> myKey;

    protected Aspect(TypedKey<T> key) {
      myKey = key;
    }

    public TypedKey<T> getKey() {
      return myKey;
    }

    public abstract T getAspect(D aspectedObject);
  }
}
