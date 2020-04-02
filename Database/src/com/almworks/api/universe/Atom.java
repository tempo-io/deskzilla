package com.almworks.api.universe;

import com.almworks.util.collections.Convertor;
import org.almworks.util.Collections15;

import java.util.Arrays;
import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public class Atom {
  private static final int BUILD_FINISHED = -1;
  private static final int NO_SUCH_JUNCTION = -1;
  private static final int JUNCTION_VALUE_IS_NOT_LONG = -2;
  private static final int BINARY_SEARCH_THRESHOLD = 20;
  public static final long[] EMPTY_LONGS = new long[0];
  public static final Particle[] EMPTY_PARTICLES = new Particle[0];

  private final long myAtomID;
  private long myUCN = -1;
  private long[] myKeys;
  //  private byte[] myByteMappedKeys; // optimization
  private Particle[] myParticles;
  private volatile int myCount = 0;
  private boolean myReferred = false;
  private boolean myDiscarding = false;

  private volatile int myLastSearchEqualKeyIndexResult = -1;

  private static final Map<KeyArrayWrapper, KeyArrayWrapper> ourKeyArrayMap =
    Collections15.linkedHashMap(100, 0.5F, true, 200);

//  private static final long[] ourCachedLongMap = new long[256]; // byte->long
//  private static int ourLastFreeCacheSlot = 0;
//  private static final Map<Long, Byte> ourCachedLongReverseMap = Collections15.hashMap();

  static {
    staticCleanup();
  }

  public Atom(long atomID, int initialJunctionCount) {
    myAtomID = atomID;
    if (initialJunctionCount > 0) {
      myKeys = new long[initialJunctionCount];
      myParticles = new Particle[initialJunctionCount];
      Arrays.fill(myKeys, Long.MAX_VALUE);
    }
  }

  public synchronized void buildFinished(long ucn) {
    if (myCount == BUILD_FINISHED)
      throw new IllegalStateException("atom build finished");
    if (myCount == 0) {
      myKeys = EMPTY_LONGS;
      myParticles = EMPTY_PARTICLES;
    } else if (myCount < myKeys.length) {
      long[] newKeys = new long[myCount];
      Particle[] newParticles = new Particle[myCount];
      System.arraycopy(myKeys, 0, newKeys, 0, myCount);
      System.arraycopy(myParticles, 0, newParticles, 0, myCount);
      myKeys = newKeys;
      myParticles = newParticles;
    }
    myCount = BUILD_FINISHED;
    myUCN = ucn;
  }

  public synchronized boolean isCommitted() {
    return myUCN >= 0;
  }

  public synchronized void buildJunction(long key, Particle particle) {
    assert particle != null;
    if (myCount == BUILD_FINISHED)
      throw new IllegalStateException("atom build finished");
    if (myCount == 0) {
      addJunction(key, particle);
      return;
    }
    assert myKeys != null;
    assert myKeys.length >= myCount;
    if (key > myKeys[myCount - 1]) {
      addJunction(key, particle);
      return;
    }
    int index = Arrays.binarySearch(myKeys, key);
    if (index >= 0) {
      setJunction(index, key, particle);
      return;
    }
    // we need to insert
    insertJunction(-index - 1, key, particle);
  }

  public synchronized void buildJunction(JunctionKey key, Particle particle) {
    buildJunction(key.getKey(), particle);
  }

  public synchronized Map<Long, Particle> copyJunctions() {
    if (myCount != BUILD_FINISHED || myKeys != null)
      return copyJunctionsUnoptimized();
    else
      return copyJunctionsOptimized();
  }

  private Map<Long, Particle> copyJunctionsOptimized() {
//    assert myCount == BUILD_FINISHED;
//    assert myByteMappedKeys != null;
//    synchronized (Atom.class) {
//      Map<Long, Particle> result = Collections15.hashMap();
//      int count = myByteMappedKeys.length;
//      for (int i = 0; i < count; i++) {
//        byte index = myByteMappedKeys[i];
//        long key = ourCachedLongMap[((int) index) & 0xFF];
//        assert key != Long.MIN_VALUE;
//        result.put(key, myParticles[i]);
//      }
//      return result;
//    }
    assert false : "disabled";
    return Collections15.emptyMap();
  }

  private Map<Long, Particle> copyJunctionsUnoptimized() {
    Map<Long, Particle> result = Collections15.hashMap();
    int count = myCount == BUILD_FINISHED ? myKeys.length : myCount;
    for (int i = 0; i < count; i++) {
      result.put(myKeys[i], myParticles[i]);
    }
    return result;
  }

  public final Particle get(long key) {
    // assume that when atom build is finished, there's no need for sync because nothing changes
    if (myCount == BUILD_FINISHED) {
      if (myKeys != null)
        return unsynchronizedGet(key);
      else
        return optimizedGet(key);
    } else {
      synchronized (this) {
        if (myCount == 0)
          return null;
        return unsynchronizedGet(key);
      }
    }
  }

  private synchronized Particle optimizedGet(long key) {
//    assert myByteMappedKeys != null;
//    byte b;
//    synchronized (Atom.class) {
//      Byte index = ourCachedLongReverseMap.get(key);
//      if (index == null) {
//         //not mapped, hence cannot exist in optimized atom
//        return null;
//      }
//      b = index;
//    }
//    for (int i = 0; i < myByteMappedKeys.length; i++)
//      if (myByteMappedKeys[i] == b)
//        return myParticles[i];
//    return null;
    assert false : "disabled";
    return null;
  }

  private Particle unsynchronizedGet(long key) {
    int index = searchEqualKeyIndex(key);
    if (index < 0)
      return null;
    if (myCount != BUILD_FINISHED && index >= myCount)
      return null;
    return myParticles[index];
  }

  private int searchEqualKeyIndex(long key) {
    // optimization - 90% hits during application startup (building indices)
    int lastResult = myLastSearchEqualKeyIndexResult;
    if (lastResult >= 0) {
      if (myKeys[lastResult] == key)
        return lastResult;
    }

    int result = doSearchEqualKeyIndex(key);
    myLastSearchEqualKeyIndexResult = result;

    return result;
  }

  private int doSearchEqualKeyIndex(long key) {
    // adaptive algorithm, depending on how many keys are in array
    // when there are not many keys, plainly seeking through the array is quicker than binarySearch.
    // see simple test (see ArraySeekPerfs)
    int length = myKeys.length;
    if (length >= BINARY_SEARCH_THRESHOLD) {
      return Arrays.binarySearch(myKeys, key);
    } else {
      for (int i = 0; i < length; i++) {
        long k = myKeys[i] - key;
        if (k == 0)
          return i;
        else if (k > 0)
          break;
      }
      return -1;
    }
  }

  public synchronized long getAtomID() {
    return myAtomID;
  }

  public synchronized boolean isReferred() {
    return myReferred;
  }

  public synchronized void setReferred() {
    myReferred = true;
  }

  public long getLong(long key) {
    Particle p = get(key);
    if (p == null)
      return NO_SUCH_JUNCTION;
    if (!(p instanceof Particle.PLong))
      return JUNCTION_VALUE_IS_NOT_LONG;
    return ((Particle.PLong) p).getValue();
  }

  public long getLong(LongJunctionKey key) {
    return getLong(key.getKey());
  }

  public String getString(long key) {
    Particle p = get(key);
    if (p == null)
      return null;
    if (!(p instanceof Particle.PIsoString))
      return null;
    return ((Particle.PIsoString) p).getValue();
  }

  public String getString(StringJunctionKey key) {
    return getString(key.getKey());
  }

  public synchronized long getUCN() {
    return myUCN;
  }

  public String toString() {
    return "A:" + myAtomID;
  }

  public synchronized <P> P visit(P passThrough, Visitor<P> visitor) {
    if (myCount == 0)
      return passThrough;
    if (myKeys != null || myCount != BUILD_FINISHED)
      return visitUnoptimized(visitor, passThrough);
    else
      return visitOptimized(visitor, passThrough);
  }

  private <P> P visitUnoptimized(Visitor<P> visitor, P passThrough) {
    int count = myCount == BUILD_FINISHED ? myKeys.length : myCount;
    for (int i = 0; i < count; i++)
      passThrough = visitor.visitJunction(myKeys[i], myParticles[i], passThrough);
    return passThrough;
  }

  private <P> P visitOptimized(Visitor<P> visitor, P passThrough) {
//    assert myCount == BUILD_FINISHED;
//    int count = myByteMappedKeys.length;
//    for (int i = 0; i < count; i++) {
//      long key;
//      synchronized (Atom.class) {
//        key = ourCachedLongMap[((int) myByteMappedKeys[i]) & 0xFF];
//      }
//      assert key != Long.MIN_VALUE;
//      passThrough = visitor.visitJunction(key, myParticles[i], passThrough);
//    }
//    return passThrough;
    assert false : "disabled";
    return passThrough;
  }

  /**
   * for optimization
   */
  public synchronized void replaceParticles(Convertor<Particle, Particle> replacer) {
    if (myCount == 0)
      return;
    int count = myCount == BUILD_FINISHED ? myKeys.length : myCount;
    for (int i = 0; i < count; i++) {
      Particle replaced = replacer.convert(myParticles[i]);
      if (replaced != null) {
        assert myParticles[i].fastEquals(replaced) : myParticles[i];
        myParticles[i] = replaced;
      }
    }
  }

  private void addJunction(long key, Particle particle) {
    assert myCount != BUILD_FINISHED;
    if (myCount == 0) {
      myKeys = new long[1];
      myParticles = new Particle[1];
      myKeys[0] = key;
      myParticles[0] = particle;
      myCount = 1;
      return;
    }
    assert myKeys != null;
    assert myParticles != null;
    assert myCount <= myKeys.length;
    assert myKeys.length == myParticles.length;
    if (myCount == myKeys.length) {
      int newLength = myKeys.length * 3 / 2 + 1;
      long[] newKeys = new long[newLength];
      Particle[] newParticles = new Particle[newLength];
      System.arraycopy(myKeys, 0, newKeys, 0, myCount);
      System.arraycopy(myParticles, 0, newParticles, 0, myCount);
      myKeys = newKeys;
      myParticles = newParticles;
      Arrays.fill(myKeys, myCount + 1, myKeys.length, Long.MAX_VALUE);
    }
    myKeys[myCount] = key;
    myParticles[myCount] = particle;
    myCount++;
  }

  private void insertJunction(int index, long key, Particle particle) {
    assert myCount != BUILD_FINISHED;
    assert myKeys.length == myParticles.length;
    if (myCount < myKeys.length) {
      if (index < myCount) {
        System.arraycopy(myKeys, index, myKeys, index + 1, myCount - index);
        System.arraycopy(myParticles, index, myParticles, index + 1, myCount - index);
      }
    } else {
      int newLength = myKeys.length * 3 / 2 + 1;
      long[] newKeys = new long[newLength];
      Particle[] newParticles = new Particle[newLength];
      if (index > 0) {
        System.arraycopy(myKeys, 0, newKeys, 0, index);
        System.arraycopy(myParticles, 0, newParticles, 0, index);
      }
      if (index < myCount) {
        System.arraycopy(myKeys, index, newKeys, index + 1, myCount - index);
        System.arraycopy(myParticles, index, newParticles, index + 1, myCount - index);
      }
      myKeys = newKeys;
      myParticles = newParticles;
      Arrays.fill(myKeys, myCount + 1, myKeys.length, Long.MAX_VALUE);
    }
    myCount++;
    myKeys[index] = key;
    myParticles[index] = particle;
    assert index == 0 || key > myKeys[index - 1];
    assert index == myCount || key < myKeys[index + 1];
  }

  private void setJunction(int index, long key, Particle particle) {
    assert myCount != BUILD_FINISHED;
    assert myKeys[index] == key;
    myParticles[index] = particle;
  }

  public void setDiscarding() {
    myDiscarding = true;
  }

  public boolean isDiscarding() {
    return myDiscarding;
  }

  public static void staticCleanup() {
    synchronized (Atom.class) {
//      Arrays.fill(ourCachedLongMap, Long.MIN_VALUE);
//      ourCachedLongReverseMap.clear();
//      ourLastFreeCacheSlot = 0;
      ourKeyArrayMap.clear();
    }
  }

  public synchronized void optimizeKeys() {
    assert myCount == BUILD_FINISHED;
    if (myCount != BUILD_FINISHED)
      return;
    assert myKeys != null;
    KeyArrayWrapper wrapper = new KeyArrayWrapper(myKeys);
    synchronized (Atom.class) {
      KeyArrayWrapper cached = ourKeyArrayMap.get(wrapper);
      if (cached != null) {
        myKeys = cached.myArray;
      } else {
        ourKeyArrayMap.put(wrapper, wrapper);
      }
    }
  }

//  public synchronized void byteOptimizeKeys() {
//    assert myCount == BUILD_FINISHED;
//    if (myCount != BUILD_FINISHED)
//      return;
//    assert myKeys != null;
//    assert myByteMappedKeys == null;
//    synchronized (Atom.class) {
//      byte[] byteMapped = new byte[myKeys.length];
//      for (int i = 0; i < myKeys.length; i++) {
//        long key = myKeys[i];
//        Byte index = ourCachedLongReverseMap.get(key);
//        if (index == null) {
//          if (ourLastFreeCacheSlot >= 256) {
//            // cannot optimize
//            return;
//          }
//          byte b = (byte) ourLastFreeCacheSlot;
//          ourLastFreeCacheSlot++;
//          ourCachedLongReverseMap.put(key, b);
//          ourCachedLongMap[((int) b) & 0xFF] = key;
//          byteMapped[i] = b;
//        } else {
//          byteMapped[i] = index;
//        }
//      }
//      myKeys = null;
//      myByteMappedKeys = byteMapped;
//    }
//  }


  public static interface Visitor<P> {
    P visitJunction(long key, Particle particle, P passThrough);
  }


  private static class KeyArrayWrapper {
    private final long[] myArray;
    private final int myHash;

    public KeyArrayWrapper(long[] array) {
      myArray = array;
      myHash = calculateHash();
    }

    public int hashCode() {
      return myHash;
    }

    private int calculateHash() {
      int code = 0;
      for (long l : myArray) {
        code += (((int) l) * 23 + ((int) (l >>> 32))) * 23;
      }
      return code;
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof KeyArrayWrapper))
        return false;
      return Arrays.equals(myArray, ((KeyArrayWrapper) obj).myArray);
    }
  }
}
