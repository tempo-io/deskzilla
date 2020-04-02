package com.almworks.universe.optimize;

import com.almworks.api.misc.WorkArea;
import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Particle;
import com.almworks.util.Env;
import com.almworks.util.collections.Convertor;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.ThreadFactory;
import com.almworks.util.io.IOUtils;
import org.almworks.util.Collections15;
import org.almworks.util.Log;

import java.io.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;

public class UniverseMemoryOptimizer extends Convertor<Particle, Particle> {
  private static final boolean OPTIMIZER_DEBUG = Env.getBoolean("optimizer.debug");

  public static final int BYTES12_VALUE1_MAP_MAX_SIZE = 10;
  public static final int BYTES16_VALUE1_MAP_MAX_SIZE = 10;
  public static final int BYTES16_VALUE4_CACHE_MAX_SIZE = 1000;
  public static final int BYTES16_VALUE4_MAP_MAX_SIZE = 10;
  public static final int BYTES9_MAP_MAX_SIZE = 10;
  private static final int PLONG_CACHE_MAX_SIZE = 50;

  /**
   * non-decreasing array containing all bytes that are not captured by more optimized classes
   * kludge - it's static to save on one pointer field in particles
   * so it does not allow for several universes
   */
//  static byte[] ourBytePlainData = new byte[1000];
//  static int ourBytePlainDataCount = 0;

  private static final int BITS = 15;
  private static final int DIM = 1 << BITS;
  private static final int MAX = 1 << (2 * BITS);
  private static final int LOW_MASK = DIM - 1;

  private static byte[][] byteData = new byte[DIM][];
  private static int byteDataSize;

  private static StringBuffer ourAccessDebugBuffer;
  private static File ourAccessDebugFile;
  private static Thread ourAccessDebugThread;

  private final ByteTableCache myBytes12Value1Cache = new ByteTableCache(BYTES12_VALUE1_MAP_MAX_SIZE) {
    protected PBytesRowOptimized createParticle(long key, byte value) {
      return createBytes12(key, ((int) value) & 0xFF);
    }
  };

  private final ByteTableCache myBytes16Value1Cache = new ByteTableCache(BYTES16_VALUE1_MAP_MAX_SIZE) {
    protected PBytesRowOptimized createParticle(long key, byte value) {
      return createBytes16Value4(key, ((int) value) & 0xFF);
    }
  };

  private final ByteTableCache myBytes9Cache = new ByteTableCache(BYTES9_MAP_MAX_SIZE) {
    protected PBytesRowOptimized createParticle(long key, byte value) {
      return createBytes9(key, value);
    }
  };

  private final Map<Integer, Object> myBytes16Value4Cache =
    Collections15.linkedHashMap(1334, 0.75F, true, BYTES16_VALUE4_CACHE_MAX_SIZE);

  private final Map<Long, Particle.PLong> myPLongCache =
    Collections15.linkedHashMap(100, 0.75F, true, PLONG_CACHE_MAX_SIZE);

  private boolean optimizationEnabled = true;
  private static final PBytesHosted ZERO_BYTES = new PBytesHosted(0, 0);

  public void optimize(Atom atom) {
    if (optimizationEnabled) {
      try {
        atom.replaceParticles(this);
        atom.optimizeKeys();
      } catch (OutOfMemoryError e) {
        // questionable...
        optimizationEnabled = false;
        Log.error(e);
      }
    }
  }

  public Particle convert(Particle value) {
    if (value == null)
      return null;
    if (value instanceof Particle.PBytes) {
      int length = value.getByteLength();
      if (length > 8 && length <= 16) {
        Particle optimized = getOptimizedOneLineBytes(value, length);
        if (optimized != null)
          return optimized;
      }
      return getOptimizedGenericBytes(value.raw());
    } else if (value instanceof Particle.PLong) {
      long plong = ((Particle.PLong) value).getValue();
      Particle.PLong p = myPLongCache.get(plong);
      if (p == null) {
        p = new Particle.PLong(plong);
        myPLongCache.put(plong, p);
      }
      return p;
    } else {
      return null;
    }
  }

  private Particle getOptimizedOneLineBytes(Particle value, int length) {
    assert length > 8 && length <= 16;
    byte[] array = value.raw();
    long key = ByteArrayAccessUtil.getLong(array, 0);
    if (length == 9) {
      return myBytes9Cache.getOptimizedParticle(key, array[8]);
    } else if (length == 12) {
      int v = ByteArrayAccessUtil.getInteger(array, 8);
      return getOptimizedBytes12(key, v);
    } else if (length == 16) {
      long v = ByteArrayAccessUtil.getLong(array, 8);
      return getOptimizedBytes16(key, v);
    } else {
      return createRareCaseOptimized(key, (byte) length, array);
    }
  }

  private Particle createRareCaseOptimized(long key, byte length, byte[] array) {
    // todo could be optimized more
    assert length > 9 : length;
    long value = 0;
    int k = 0;
    for (int i = length - 1; i >= 8; i--) {
      value |= (((long) array[i]) & 0xFF) << k;
      k += 8;
    }
    return new PBytesRareCase(key, (byte) (length - 8), value);
  }

  private Particle getOptimizedBytes12(long key, int value) {
    if (value >= 0 && value < 256)
      return myBytes12Value1Cache.getOptimizedParticle(key, (byte) (value & 0xFF));
    else
      return createBytes12(key, value);
  }

  private PBytesRowOptimized createBytes12(long key, int value) {
    if (key >= 0 && key < 0x100000000L)
      return new PBytes12Key4((int) key, value);
    else
      return new PBytes12Key8(key, value);
  }

  private Particle getOptimizedBytes16(long key, long value) {
    if (value >= 0 && value < 256)
      return myBytes16Value1Cache.getOptimizedParticle(key, (byte) (value & 0xFF));
    else if (value >= 0 && value < 0x100000000L)
      return getOptimizedBytes16Value4(key, (int) (value & 0xFFFFFFFFL));
    else
      return createBytes16Value8(key, value);
  }

  private Particle getOptimizedBytes16Value4(long key, int value) {
    Object obj = myBytes16Value4Cache.get(value);
    if (obj == null) {
      PBytesRowOptimized p = createBytes16Value4(key, value);
      myBytes16Value4Cache.put(value, p);
      return p;
    } else if (obj instanceof PBytesRowOptimized) {
      PBytesRowOptimized cached = ((PBytesRowOptimized) obj);
      long cachedKey = cached.getKey();
      if (cachedKey == key) {
        return cached;
      } else {
        Map<Long, PBytesRowOptimized> map = createKeyParticleMap(BYTES16_VALUE4_MAP_MAX_SIZE);
        map.put(cachedKey, cached);
        PBytesRowOptimized p = createBytes16Value4(key, value);
        map.put(key, p);
        myBytes16Value4Cache.put(value, map);
        return p;
      }
    } else if (obj instanceof Map) {
      Map map = ((Map) obj);
      PBytesRowOptimized p = (PBytesRowOptimized) map.get(key);
      if (p == null) {
        p = createBytes16Value4(key, value);
        map.put(key, p);
      }
      return p;
    } else {
      assert false : obj;
      return createBytes16Value4(key, value);
    }
  }

  private static Map<Long, PBytesRowOptimized> createKeyParticleMap(final int maxSize) {
    float factor = 0.75F;
    int initialCapacity = (int) (maxSize / factor + 1);
    Map<Long, PBytesRowOptimized> map = Collections15.linkedHashMap(initialCapacity, factor, true, maxSize);
    return map;
  }

  private PBytesRowOptimized createBytes16Value4(long key, int value) {
    if (key >= 0 && key < 0x100000000L)
      return new PBytes16Key4Value4((int) key, value);
    else
      return new PBytes16Key8Value8(key, ((long) value) & 0xFFFFFFFFL);
  }

  private Particle createBytes16Value8(long key, long value) {
    return new PBytes16Key8Value8(key, value);
  }

  private static synchronized Particle getOptimizedGenericBytes(byte[] bytes) {
    final int length = bytes.length;
    if (length <= 0 || length > 1000000000) {
      assert length == 0 : length;
      return ZERO_BYTES;
    }
    int offset = byteDataSize;
    int newSize = byteDataSize + length;
    if (newSize > MAX) {
      assert false : newSize;
      throw new OutOfMemoryError();
    }
    int xmin = offset >> BITS;
    int xmax = (newSize - 1) >> BITS;
    int targetY = offset & LOW_MASK;
    int sourceP = 0;
    int remaining = length;
    for (int x = xmin; x <= xmax; x++) {
      if (byteData[x] == null) {
        byteData[x] = new byte[DIM];
      }
      assert
        length == sourceP + remaining : length + " " + sourceP + " " + remaining + " " + x + " " + xmin + " " + xmax;
      int copylen = DIM - targetY;
      if (copylen > remaining)
        copylen = remaining;
      System.arraycopy(bytes, sourceP, byteData[x], targetY, copylen);
      remaining -= copylen;
      sourceP += copylen;
      targetY = 0;
    }
    assert remaining == 0 : length + " " + sourceP + " " + remaining + " " + " " + xmin + " " + xmax;
    assert sourceP == bytes.length;
    byteDataSize = newSize;
    return new PBytesHosted(offset, length);
  }

  private PBytesRowOptimized createBytes9(long key, byte value) {
    if (key >= 0 && key < 0x100000000L)
      return new PBytes9Key4((int) key, value);
    else
      return new PBytes9Key8(key, value);
  }

  public static synchronized void staticCleanup() {
    byteData = new byte[DIM][];
    byteDataSize = 0;
    Atom.staticCleanup();
  }

  public static boolean compareChunks(int offsetA, int offsetB, int length) {
    if (offsetA == offsetB)
      return true;
    // chunks cannot overlap
    assert offsetA >= offsetB + length || offsetB >= offsetA + length : offsetA + " " + offsetB + " " + length;

    assert readStat(offsetA, length);
    assert readStat(offsetB, length);
    int xA = offsetA >> BITS;
    int yA = offsetA & LOW_MASK;
    int xB = offsetB >> BITS;
    int yB = offsetB & LOW_MASK;
    byte[] arrA = byteData[xA];
    byte[] arrB = byteData[xB];
    while (length > 0) {
      int compareLength = length;
      int leftA = DIM - yA;
      int leftB = DIM - yB;
      if (compareLength > leftA)
        compareLength = leftA;
      if (compareLength > leftB)
        compareLength = leftB;
      for (int i = 0; i < compareLength; i++) {
        if (arrA[yA++] != arrB[yB++])
          return false;
      }
      length -= compareLength;
      if (length > 0) {
        assert yA == DIM || yB == DIM : yA + " " + yB;
        if (yA == DIM) {
          yA = 0;
          arrA = byteData[++xA];
        }
        if (yB == DIM) {
          yB = 0;
          arrB = byteData[++xB];
        }
      }
    }
    return true;
  }

  public static boolean compareArray(byte[] array, int offset) {
    assert readStat(offset, array.length);

    int length = array.length;
    int x = offset >> BITS;
    int y = offset & LOW_MASK;
    int arrayOffset = 0;
    byte[] arr = byteData[x];
    while (length > 0) {
      int compareLength = DIM - y;
      if (compareLength > length)
        compareLength = length;
      for (int i = 0; i < compareLength; i++) {
        if (arr[y++] != array[arrayOffset++])
          return false;
      }
      length -= compareLength;
      if (length > 0) {
        if (y == DIM) {
          y = 0;
          arr = byteData[++x];
        } else {
          assert false : y + " " + length;
        }
      }
    }
    return true;
  }

  public static byte[] copy(int offset, int length) {
    assert readStat(offset, length);
    byte[] result = new byte[length];
    int x = offset >> BITS;
    int y = offset & LOW_MASK;
    int resultOffset = 0;
    while (length > 0) {
      int sz = DIM - y;
      if (sz > length)
        sz = length;
      System.arraycopy(byteData[x], y, result, resultOffset, sz);
      length -= sz;
      resultOffset += sz;
      y = 0;
      x++;
    }
    assert resultOffset == result.length;
    return result;
  }

  public static byte getByte(int offset) {
    assert readStat(offset, 1);
    return byteData[offset >> BITS][offset & LOW_MASK];
  }

  private static boolean readStat(int offset, int length) {
    if (!OPTIMIZER_DEBUG)
      return true;
    long nano = System.currentTimeMillis();
    synchronized (UniverseMemoryOptimizer.class) {
      if (ourAccessDebugFile == null) {
        WorkArea workArea = Context.require(WorkArea.class);
        ourAccessDebugFile = new File(workArea.getLogDir(), "optimizer.log");
      }
      if (ourAccessDebugBuffer == null) {
        ourAccessDebugBuffer = new StringBuffer();
        ourAccessDebugBuffer.append("\n");
        ourAccessDebugBuffer.append(nano);
        ourAccessDebugBuffer.append(" starting log ");
        ourAccessDebugBuffer.append(
          DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date()));
        ourAccessDebugBuffer.append(" ==================================================\n");
      }
      if (ourAccessDebugThread == null) {
        ourAccessDebugThread = ThreadFactory.create("optimizer.debug.dumper", new Runnable() {
          public void run() {
            try {
              while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(5000);
                dumpStats();
              }
            } catch (InterruptedException e) {
              // ignore
            }
          }
        });
        ourAccessDebugThread.setDaemon(true);
        ourAccessDebugThread.setPriority(Thread.MAX_PRIORITY - 1);
        ourAccessDebugThread.start();
      }
      ourAccessDebugBuffer.append(nano);
      ourAccessDebugBuffer.append(' ');
      ourAccessDebugBuffer.append(offset);
      ourAccessDebugBuffer.append(' ');
      ourAccessDebugBuffer.append(length);
      ourAccessDebugBuffer.append('\n');
      if (ourAccessDebugBuffer.length() > 1000000) {
        dumpStats();
      }
    }
    return true;
  }

  private static void dumpStats() {
    synchronized (UniverseMemoryOptimizer.class) {
      StringBuffer buffer = ourAccessDebugBuffer;
      if (buffer == null)
        return;
      if (buffer.length() == 0)
        return;
      File file = ourAccessDebugFile;
      if (file == null)
        return;
      FileOutputStream stream = null;
      BufferedOutputStream bos = null;
      PrintWriter writer = null;
      try {
        stream = new FileOutputStream(file, true);
        bos = new BufferedOutputStream(stream);
        writer = new PrintWriter(bos);
        writer.append(buffer);
      } catch (FileNotFoundException e) {
        // cannot write, skip and remove
      } finally {
        IOUtils.closeWriterIgnoreExceptions(writer);
        IOUtils.closeStreamIgnoreExceptions(bos);
        IOUtils.closeStreamIgnoreExceptions(stream);
      }
      ourAccessDebugBuffer.setLength(0);
    }
  }

  public static int size() {
    return byteDataSize;
  }


  private static abstract class ByteTableCache {
    /**
     * This table stores cached instances of particles that have 9 bytes of content.
     * The index in this table corresponds to the latest byte in the content.
     * The object in the table could be either Particle or Map<Long, Particle>.
     */
    private final Object[] myTable = new Object[256];
    private final int myMapMaxSize;

    public ByteTableCache(int mapMaxSize) {
      myMapMaxSize = mapMaxSize;
    }

    protected abstract PBytesRowOptimized createParticle(long key, byte value);

    public Particle getOptimizedParticle(long key, byte value) {
      int b = ((int) value) & 0xFF;
      assert b >= 0 && b < 256;
      Object obj = myTable[b];
      if (obj instanceof PBytesRowOptimized) {
        PBytesRowOptimized cached = ((PBytesRowOptimized) obj);
        long cachedKey = cached.getKey();
        if (cachedKey == key) {
          return cached;
        } else {
          Map<Long, PBytesRowOptimized> map = createKeyParticleMap(myMapMaxSize);
          map.put(cachedKey, cached);
          PBytesRowOptimized p = createParticle(key, value);
          map.put(key, p);
          myTable[b] = map;
          return p;
        }
      } else if (obj == null) {
        PBytesRowOptimized p = createParticle(key, value);
        myTable[b] = p;
        return p;
      } else if (obj instanceof Map) {
        Map map = ((Map) obj);
        PBytesRowOptimized p = (PBytesRowOptimized) map.get(key);
        if (p == null) {
          p = createParticle(key, value);
          map.put(key, p);
        }
        return p;
      } else {
        assert false : obj;
        return createParticle(key, value);
      }
    }
  }
}
