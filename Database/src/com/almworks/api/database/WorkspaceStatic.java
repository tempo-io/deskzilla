package com.almworks.api.database;

import com.almworks.api.database.typed.TypedArtifact;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import util.concurrent.SynchronizedBoolean;

import java.util.Map;
import java.util.Set;

/**
 * @author dyoma
 */
public class WorkspaceStatic {
  private static final Map<TypedKey, TypedArtifact> ourSystemObjects = Collections15.synchronizedHashMap();
  private static final SynchronizedBoolean myLoaded = new SynchronizedBoolean(false);
  private static final Object myLock = myLoaded.getLock();

  public static void load(Workspace workspace, Set<TypedKey> keys) {
    synchronized (myLock) {
      assert !myLoaded.get();
      for (TypedKey<? extends TypedArtifact> key : keys) {
        TypedArtifact object = workspace.getSystemObject(key);
        if (object != null) {
          ourSystemObjects.put(key, object);
        }
      }
      myLoaded.set(true);
    }
  }

  public static void cleanup() {
    synchronized (myLock) {
      ourSystemObjects.clear();
      myLoaded.set(false);
    }
  }

  public static <T extends TypedArtifact> T getSystemArtifact(TypedKey<T> key) {
    synchronized(myLock) {
      assert myLoaded.get();
      TypedArtifact artifact = ourSystemObjects.get(key);
      assert artifact != null : key;
      return (T) artifact;
    }
  }
}
