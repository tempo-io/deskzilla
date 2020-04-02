package com.almworks.database;

import com.almworks.api.database.typed.TypedArtifact;
import org.almworks.util.*;

import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public class SystemObjectsCache implements SystemObjectResolver {
  private final SystemObjectResolver myUnderlying;
  private final Map<TypedKey, TypedArtifact> mySystemObjects = Collections15.synchronizedHashMap();

  public SystemObjectsCache(SystemObjectResolver underlying) {
    assert underlying != null;
    myUnderlying = underlying;
  }

  public <T extends TypedArtifact> T getSystemObject(TypedKey<T> artifactKey) {
    TypedArtifact cached = mySystemObjects.get(artifactKey);
    if (cached != null) {
      try {
        return (T) cached;
      } catch (ClassCastException e) {
        Log.warn("cached object is of wrong type [" + cached + "]", e);
        mySystemObjects.remove(artifactKey);
        // fall through
      }
    }

    T result = myUnderlying.getSystemObject(artifactKey);
    assert result != null : artifactKey;
    if (result != null)
      mySystemObjects.put(artifactKey, result);
    return result;
  }
}
