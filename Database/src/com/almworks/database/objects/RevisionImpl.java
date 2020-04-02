package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Particle;
import com.almworks.database.*;
import com.almworks.util.Env;
import com.almworks.util.cache2.LongKeyedObject;
import com.almworks.util.commons.Lazy;
import org.almworks.util.*;
import util.concurrent.Synchronized;

import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.Map;

public class RevisionImpl extends LongKeyedObject {
  public static final boolean IGNORE_CONCURRENT_CACHE = !Env.getBoolean("no.ignore.concurrent.cache", false);

  private final Atom myAtom;
  private final Basis myBasis;
  private final Lazy<Identity> myIdentity;

  private volatile SoftReference<ValueCache> myCacheLocalDefault;
  private volatile SoftReference<ValueCache> myCacheRemote;
  private static final long LOADING_WAIT_TIMEOUT = 300;

  private static final Object NULL_SENTINEL = new Object();

  public RevisionImpl(Basis basis, long key, Atom atom) {
    super(key);
    myBasis = basis;
    myAtom = atom;
    myIdentity = new Lazy<Identity>() {
      public Identity instantiate() {
        return new SimpleIdentity(myBasis.getObjectAddress(myKey));
      }
    };
  }

  public Value get(ArtifactPointer pointer, RevisionIterator revisionIterator) {
    try {
      assert pointer != null;
      // this should not cause reentrancy
      Artifact artifact = pointer.getArtifact();
      assert artifact != null;
      int artifactKey = (int) artifact.getKey();
      boolean valueLoaded;
      Synchronized<Thread> loadingThreadSync;
      boolean ignoreCache;
      ValueCache cache = getCache(revisionIterator);
      int index0;
      synchronized (cache) {
        index0 = cache.getIndex(artifactKey);
        Object v = cache.getObjectByIndex(index0);
        if (v == NULL_SENTINEL) {
          return null;
        } else if (v instanceof Value) {
          return (Value) v;
        } else if (v instanceof Thread) {
          if (v == Thread.currentThread()) {
            Log.error("recursive get() for " + this + ", attribute " + pointer);
            throw new IllegalStateException("recursive get");
          }
          assert index0 >= 0;
          if (!IGNORE_CONCURRENT_CACHE) {
            cache.wait(LOADING_WAIT_TIMEOUT);
            cache.notify();
            v = cache.getObjectByIndex(index0);
            if (v == NULL_SENTINEL) {
              return null;
            } else if (v instanceof Value) {
              return (Value) v;
            }
          }
          ignoreCache = true;
        } else {
          assert v == null : v;
          index0 = cache.setObjectByIndex(index0, artifactKey, Thread.currentThread());
          assert index0 >= 0 : index0;
          ignoreCache = false;
        }
      }

      if (ignoreCache) {
        // can't wait anymore for other thread to load a value (maybe it's a deadlock?)
        // load value myself, ignore cache
        if (IGNORE_CONCURRENT_CACHE) {
          Log.debug(this + ": icc [" + artifact + "]");
        } else {
          Log.warn(this + ": timeout [" + artifact + "]");
        }
        return loadValue(artifact, revisionIterator);
      }

      Value value = null;
      try {
        assert index0 >= 0 : index0 + " " + artifactKey;
        value = loadValue(artifact, revisionIterator);
      } finally {
        try {
          synchronized (cache) {
            int index1 = cache.getIndex(artifactKey, index0);
            cache.setObjectByIndex(index1, artifactKey, value == null ? NULL_SENTINEL : value);
            if (!IGNORE_CONCURRENT_CACHE) {
              cache.notify();
            }
          }
        } catch (Throwable t) {
          // no exception throwing in finally{}
          Log.error("error after loading value", t);
        }
      }
      return value;
    } catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
  }

  public Atom getAtom() {
    return myAtom;
  }

  public Identity getIdentity() {
    return myIdentity.get();
  }

  public WCN getWCN() {
    return WCN.createWCN(myAtom.getUCN());
  }

  public void invalidateValuesCache(RevisionAccess access) {
    if (access == RevisionAccess.ACCESS_MAINCHAIN && myCacheRemote != null) {
      synchronized (this) {
        myCacheRemote = null;
      }
    } else if (myCacheLocalDefault != null) {
      synchronized (this) {
        myCacheLocalDefault = null;
      }
    }
  }

  private ValueCache getCache(RevisionIterator revisionIterator) {
    return getCache(revisionIterator.getStrategy());
  }

  private ValueCache getCache(RevisionAccess key) {
    // goal: performance
    if (key == RevisionAccess.ACCESS_MAINCHAIN) {
      SoftReference<ValueCache> ref = myCacheRemote;
      ValueCache cache = ref == null ? null : ref.get();
      // we can get an uninitialized object, so check
      if (cache == null) {
        synchronized (this) {
          ref = myCacheRemote;
          cache = ref == null ? null : ref.get();
          if (cache == null)
            cache = new ValueCache();
          myCacheRemote = new SoftReference<ValueCache>(cache);
        }
      }
      return cache;
    } else {
      SoftReference<ValueCache> ref = myCacheLocalDefault;
      ValueCache cache = ref == null ? null : ref.get();
      // we can get an uninitialized object, so check
      if (cache == null) {
        synchronized (this) {
          ref = myCacheLocalDefault;
          cache = ref == null ? null : ref.get();
          if (cache == null)
            cache = new ValueCache();
          myCacheLocalDefault = new SoftReference<ValueCache>(cache);
        }
      }
      return cache;
    }
  }

  private Value loadValue(final Artifact attribute, final RevisionIterator revisionIterator) {
    do {
      int attempt = 0;
      try {
        long attributeID = myBasis.getAtomID(attribute);
        Particle particle = myAtom.get(attributeID);
        if (particle == null) {
          Atom atom = myAtom;
          long startAtomId = myAtom.getAtomID();
          int step = 0;
          while (particle == null) {
            RevisionWithInternals prev = revisionIterator.getPreviousRevision(myBasis, atom);
            if (prev == null) {
              // not found
              break;
            }
            atom = prev.getAtom();
            if (atom == null) {
              assert false : myAtom + " " + step + " " + prev;
              break;
            }
            step++;
            if (atom.getAtomID() == startAtomId) {
              Log.warn("chain cycled: from " + startAtomId + " with " + revisionIterator + " " + step + " steps");
              break;
            }
            particle = atom.get(attributeID);
          }
        }
        Value value = particle == null ? null : extractValue(particle);
        return value;
      } catch (DatabaseInconsistentException e) {
        myBasis.ourConsistencyWrapper.handle(e, attempt++);
      }
    } while (true);
  }

  private Value extractValue(Particle particle) throws DatabaseInconsistentException {
    Value value;
    value = myBasis.ourValueFactory.unmarshall(particle);
    if (value == null) {
      throw new DatabaseInconsistentException("cannot understand value of " + particle + " (" + myKey + ")");
    } else {
      if (value == ValueFactoryImpl.UNSET) {
        // specifically unset
        value = null;
      }
    }
    return value;
  }

  public Map<ArtifactPointer, Value> getChanges() {
    while (true) {
      try {
        Map<Long, Particle> atomContent = myAtom.copyJunctions();
        Map<ArtifactPointer, Value> result = Collections15.hashMap();
        for (Iterator<Map.Entry<Long, Particle>> ii = atomContent.entrySet().iterator(); ii.hasNext();) {
          Map.Entry<Long, Particle> entry = ii.next();
          long id = entry.getKey().longValue();
          if (id < 0) {
            // skip system junctions
            continue;
          }
          ArtifactProxy attribute = myBasis.getArtifact(id);
          Value value = extractValue(entry.getValue());
          result.put(attribute, value);
        }
        return result;
      } catch (DatabaseInconsistentException e) {
        myBasis.ourConsistencyWrapper.handle(e, -1);
      }
    }
  }

  private static final class ValueCache {
    private static final int INITIAL_KEYMAP_CAPACITY = 4;
    private static final int INCREMENT_KEYMAP_CAPACITY = 8;

    private int[] myKeys;
    private Object[] myValues;
    private int myCount;

    public int getIndex(int artifactKey, int hintIndex) {
      assert Thread.holdsLock(this);
      assert hintIndex >= 0;
      try {
        if (myKeys != null && hintIndex < myCount && myKeys[hintIndex] == artifactKey)
          return hintIndex;
      } catch (IndexOutOfBoundsException e) {
        // ignore
      }
      return getIndex(artifactKey);
    }

    public int getIndex(int artifactKey) {
      assert Thread.holdsLock(this);
      int[] keys = myKeys;
      if (keys == null)
        return -1;
      return ArrayUtil.binarySearch(artifactKey, keys, 0, myCount);
    }

    public Object getObjectByIndex(int index) {
      assert Thread.holdsLock(this);
      if (index < 0 || index >= myCount)
        return null;
      try {
        return myValues[index];
      } catch (NullPointerException e) {
        assert false : e;
        return null;
      } catch (IndexOutOfBoundsException e) {
        assert false : e;
        return null;
      }
    }

    public int setObjectByIndex(int index, int artifactKey, Object value) {
      assert value != null : this;
      assert Thread.holdsLock(this);
      if (index >= 0) {
        assert myKeys != null : myKeys + " " + myValues;
        assert myValues != null : myKeys + " " + myValues;
        assert myKeys.length > index : myKeys + " " + myValues;
        assert myValues.length == myKeys.length : myKeys + " " + myValues;
        assert myKeys[index] == artifactKey : artifactKey + " " + myKeys + " " + myValues;
        myValues[index] = value;
      } else {
        assert getIndex(artifactKey) == index : artifactKey + " " + myKeys + " " + myValues;
        int p = -index - 1;
        if (myKeys == null) {
          assert p == 0 : index;
          assert myCount == 0 : myCount;
          myKeys = new int[INITIAL_KEYMAP_CAPACITY];
          myValues = new Object[INITIAL_KEYMAP_CAPACITY];
        } else {
          assert myValues != null && myKeys.length == myValues.length : myKeys + " " + myValues;
          assert p >= 0 && p <= myCount : p + " " + myCount;
          if (myKeys.length == myCount) {
            int[] keys = new int[myKeys.length + INCREMENT_KEYMAP_CAPACITY];
            Object[] values = new Object[myValues.length + INCREMENT_KEYMAP_CAPACITY];
            for (int i = 0, j = 0; i < myCount; i++, j++) {
              if (i == p)
                j++;
              keys[j] = myKeys[i];
              values[j] = myValues[i];
            }
            myKeys = keys;
            myValues = values;
          } else {
            assert myKeys.length > myCount : myCount + " " + myKeys.length;
            for (int i = myCount - 1, j = myCount; i >= p; i--, j--) {
              myKeys[j] = myKeys[i];
              myValues[j] = myValues[i];
            }
          }
        }
        myKeys[p] = artifactKey;
        myValues[p] = value;
        myCount++;
        index = p;
      }
      return index;
    }
  }
}
