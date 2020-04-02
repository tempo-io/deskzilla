package com.almworks.syncreg;

import com.almworks.api.store.Store;
import com.almworks.api.syncreg.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.Env;
import com.almworks.util.io.persist.FormatException;
import com.almworks.util.threads.ThreadSafe;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;
import util.external.CompactInt;

import java.io.*;

class SyncCubeRegistryImpl implements SyncCubeRegistry {
  private static final int MAX_DIMENSIONS = 8;
  private static final int FORMAT_SIGNATURE = 0xC0BE50FF;

  private final EquidimensionalSet[] mySets = new EquidimensionalSet[MAX_DIMENSIONS];
  private boolean myAllSynchronized = false;
  private final Object myLock = new Object();
  private final SyncRegistry.Listener myListener;

  private boolean myDump = Env.getBoolean("cube.dump");

  SyncCubeRegistryImpl() {
    this(new SyncRegistry.Listener() {
      public void onSyncRegistryChanged(boolean moreSynchronized, boolean lessSynchronized) {
      }
    });
  }

  public SyncCubeRegistryImpl(SyncRegistry.Listener listener) {
    myListener = listener;
    for (int i = 0; i < mySets.length; i++)
      mySets[i] = new EquidimensionalSet(i + 1);
  }

  @ThreadSafe
  public boolean isSynced(@Nullable Hypercube<DBAttribute<?>, Long> cube) {
    if (cube == null) return false;
    synchronized (myLock) {
      if (myAllSynchronized)
        return true;
      int axes = cube.getAxisCount();
      if (axes == 0)
        return false;
      NumberedCube numberedCube = SyncCubeUtils.convert(cube);
      for (int i = 1; i <= axes && i <= MAX_DIMENSIONS; i++) {
        EquidimensionalSet set = mySets[i - 1];
        if (set.encompasses(numberedCube))
          return true;
      }
      return false;
    }
  }

  public void setSynced(@NotNull Hypercube<DBAttribute<?>, Long> cube) {
    if (cube == null) return;
    int axisCount = cube.getAxisCount();
    if (axisCount == 0) {
      boolean already;
      synchronized (myLock) {
        already = myAllSynchronized;
        myAllSynchronized = true;
        for (EquidimensionalSet set : mySets) {
          set.clear();
        }
      }
      if (!already)
        myListener.onSyncRegistryChanged(true, false);
    } else {
      synchronized (myLock) {
        if (myAllSynchronized)
          return;
        NumberedCube numberedCube = SyncCubeUtils.convert(cube);
        for (int i = 0; i < MAX_DIMENSIONS; i++) {
          EquidimensionalSet set = mySets[i];
          if (i + 1 <= axisCount) {
            // for same-dim or higher-dim sets, check if the cube is contained already
            if (set.encompasses(numberedCube))
              return;
          }
          if (i + 1 >= axisCount) {
            // for same-dim or lower-dim sets, check if they are outdated
            set.removeEncompassedBy(numberedCube);
          }
        }
        if (axisCount <= MAX_DIMENSIONS)
          mySets[axisCount - 1].addCube(numberedCube);
      }
      dump();
      myListener.onSyncRegistryChanged(true, false);
    }
  }

  public void setUnsynced(@NotNull Hypercube<DBAttribute<?>, Long> cube) {
    synchronized (myLock) {
      myAllSynchronized = false;
      int axisCount = cube.getAxisCount();
      if (axisCount == 0) {
        for (EquidimensionalSet set : mySets) {
          set.clear();
        }
      } else {
        NumberedCube numberedCube = SyncCubeUtils.convert(cube);
        for (int i = 0; i < MAX_DIMENSIONS; i++) {
          EquidimensionalSet set = mySets[i];
          if (i + 1 <= axisCount) {
            set.removeEncompassing(numberedCube);
          }
          if (i + 1 >= axisCount) {
            set.removeEncompassedBy(numberedCube);
          }
        }
      }
    }
    dump();
    myListener.onSyncRegistryChanged(false, true);
  }

  private void dump() {
    if (!myDump)
      return;
    synchronized (myLock) {
      Log.debug("=========== cubesync.dump ===========");
      Log.debug("=== full sync: " + myAllSynchronized);
      for (int i = 0; i < MAX_DIMENSIONS; i++) {
        mySets[i].dump();
      }
      Log.debug("=========== cubesync.dump ===========");
    }
  }

  public boolean load(Store store, String key) {
    synchronized (myLock) {
      byte[] bytes = store.access(key).load();
      if (bytes == null) {
        Log.debug("cannot restore cubes");
        return false;
      }
      ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
      DataInputStream in = new DataInputStream(stream);
      try {
        load(in);
        return true;
      } catch (IOException e) {
        Log.debug("cannot restore cubes", e);
        return false;
      }
    }
  }

  public void save(Store store, String key) {
    synchronized (myLock) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(stream);
      try {
        save(out);
        out.close();
        store.access(key).store(stream.toByteArray());
      } catch (IOException e) {
        // weird
        assert false : e;
        Log.warn("cannot write cubes");
      }
    }
  }

  /**
   * needed for testing. we don't override equals() because this class is not used for
   * hashable objects
   */
  boolean equalRegistry(SyncCubeRegistryImpl that) {
    if (that == null)
      return false;
    if (myAllSynchronized != that.myAllSynchronized)
      return false;
    if (mySets.length != that.mySets.length)
      return false;
    for (int i = 0; i < mySets.length; i++) {
      if (!mySets[i].equalSet(that.mySets[i]))
        return false;
    }
    return true;
  }

  void load(DataInput in) throws IOException {
    int signature = in.readInt();
    if (signature != FORMAT_SIGNATURE)
      throw new FormatException(Long.toHexString(signature));
    boolean allSynchronized = in.readBoolean();
    int setCount = CompactInt.readInt(in);
    if (setCount <= 0 || setCount > 100)
      throw new FormatException("" + setCount);
    EquidimensionalSet[] sets = new EquidimensionalSet[MAX_DIMENSIONS];
    for (int i = 0; i < MAX_DIMENSIONS; i++) {
      sets[i] = new EquidimensionalSet(i + 1);
      if (i < setCount) {
        sets[i].load(in);
      }
    }
    myAllSynchronized = allSynchronized;
    System.arraycopy(sets, 0, mySets, 0, Math.min(mySets.length, sets.length));
    dump();
  }

  void save(DataOutput out) throws IOException {
    out.writeInt(FORMAT_SIGNATURE);
    out.writeBoolean(myAllSynchronized);
    CompactInt.writeInt(out, mySets.length);
    for (EquidimensionalSet set : mySets) {
      set.save(out);
    }
  }
}
