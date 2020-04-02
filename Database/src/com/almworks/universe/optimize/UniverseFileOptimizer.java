package com.almworks.universe.optimize;

import com.almworks.api.database.util.WorkspaceUtils;
import com.almworks.util.Env;
import org.almworks.util.*;

import java.io.File;
import java.io.RandomAccessFile;

public class UniverseFileOptimizer {
  private static final String SETTING = "no.file.optimizer";
  private static final boolean USE_FILE_OPTIMIZER = !Env.getBoolean(SETTING);

  private static volatile UniverseFileOptimizer myInstance = null;

  private final File myFile;
  private final RandomAccessFile myRaf;
  private final Object myAccessLock;

  public UniverseFileOptimizer(File file, RandomAccessFile raf, Object accessLock) {
    myFile = file;
    myRaf = raf;
    myAccessLock = accessLock;
  }

  private void read(int offset, int length, byte[] target, int targetOffset) {
    try {
      synchronized (myAccessLock) {
        myRaf.seek(offset);
        myRaf.readFully(target, targetOffset, length);
      }
    } catch (Exception e) {
      fail(e);
    }
  }

  public static synchronized void start(File file, RandomAccessFile raf, Object accessLock) {
    if (!USE_FILE_OPTIMIZER)
      return;
    UniverseFileOptimizer instance = myInstance;
    if (instance != null)
      throw new IllegalStateException(instance + ":" + file + ":" + raf);
    myInstance = new UniverseFileOptimizer(file, raf, accessLock);
  }

  public static synchronized void stop(File file) {
    if (!USE_FILE_OPTIMIZER)
      return;
    UniverseFileOptimizer instance = myInstance;
    if (instance == null)
      throw new IllegalStateException("no instance: " + file);
    if (!Util.equals(instance.myFile, file))
      throw new IllegalStateException("files don't match: " + file);
    myInstance = null;
  }

  public static synchronized void readInto(int offset, int length, byte[] target, int targetOffset) {
    assert USE_FILE_OPTIMIZER;
    UniverseFileOptimizer instance = myInstance;
    assert instance != null;
    instance.read(offset, length, target, targetOffset);
  }

  public static boolean isActive() {
    return USE_FILE_OPTIMIZER && myInstance != null;
  }

  private static void fail(Exception e) {
    Log.warn("problem with db optimizer: try to disable it with -D" + SETTING + "=true", e);
    Throwable exception = WorkspaceUtils.onFatalDatabaseProblem(e);
    // should not reach here
    throw new Failure("database failed", e);
  }
}
