package com.almworks.database.debug;

import com.almworks.api.database.Workspace;
import com.almworks.api.universe.Universe;
import com.almworks.database.WorkspaceImpl;
import com.almworks.database.bitmap.AbstractBitmapIndex;
import com.almworks.database.bitmap.BitmapIndexManager;
import org.almworks.util.Failure;

import java.io.File;
import java.io.IOException;

/**
 * :todoc:
 *
 * @author sereda
 */
public class Dumper {
  private static final String DUMP_DIRNAME = "dump";

  public static void dumpWU(Workspace workspace, File baseDir) throws IOException, InterruptedException {
    if (!baseDir.isDirectory())
      throw new IllegalArgumentException("bad or inexistent directory " + baseDir);
    Exception exception = null;
    try {
      WorkspaceImpl impl = (WorkspaceImpl) workspace;
      Universe universe = impl.getUniverse();
      UniverseDumper.dump(universe, new File(getDumpDir(baseDir), "dump.uni").getPath());
    } catch (Exception e) {
      exception = e;
    }
    try {
      DatabaseDumper.dump(workspace, new File(getDumpDir(baseDir), "dump.wks").getPath());
    } catch (Exception e) {
      exception = e;
    }
    if (exception != null)
      throw new Failure(exception);
  }

  private static File getDumpDir(File baseDir) throws IOException {
    File dumpDir = new File(baseDir, DUMP_DIRNAME);
    if (dumpDir.exists()) {
      if (!dumpDir.isDirectory())
        if (!dumpDir.delete())
          throw new IOException("cannot create dump dir " + dumpDir);
    } else {
      if (!dumpDir.mkdir())
        throw new IOException("cannot create dump dir " + dumpDir);
    }
    return dumpDir;
  }

  public static void dumpIndexes(Workspace workspace, File baseDir) throws IOException {
    BitmapIndexManager mgr = ((WorkspaceImpl)workspace).getIndexManager();
    AbstractBitmapIndex[] indexes = mgr.getIndexes();
    for (int i = 0; i < indexes.length; i++) {
      AbstractBitmapIndex index = indexes[i];
      String key = index.getKey().getIndexKey();
      key = key.replaceAll("\\:", ".");
      IndexDumper.dump(index, new File(getDumpDir(baseDir), "bitmap." + key));
    }
  }
}
