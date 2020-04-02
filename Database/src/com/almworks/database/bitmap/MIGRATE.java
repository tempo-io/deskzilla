package com.almworks.database.bitmap;

import com.almworks.api.install.Setup;
import com.almworks.util.files.FileUtil;
import org.almworks.util.Log;
import org.almworks.util.RuntimeInterruptedException;

import java.io.File;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

public class MIGRATE {
  private static final String KEY = "migrate-beta3a";
  private static final Pattern BITMAP_INDEX_FILE = Pattern.compile("bi.[0-9a-f\\-]+", Pattern.CASE_INSENSITIVE);

  // todo remove ! replace with migrating service
  public static void migrateToBeta3a(File databaseDir) {
    Preferences preferences = Setup.getUserPreferences();
    if (preferences.getBoolean(KEY, false))
      return;
    Log.warn("migrating from previous version");

    dropIndexes(databaseDir, true);

    preferences.putBoolean(KEY, true);
    try {
      preferences.flush();
    } catch (BackingStoreException e) {
      Log.debug("cannot store migration key");
    }
  }

  public static void dropIndexes(File databaseDir, boolean dropIndexIndex) {
    if (!databaseDir.isDirectory())
      return;
    File[] files = databaseDir.listFiles();
    if (files == null)
      return;

    Log.warn("dropping indexes");
    InterruptedException interrupted = null;
    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      if (!file.isFile())
        continue;
      String name = file.getName();
      boolean delete = false;
      if (dropIndexIndex && BitmapIndexFileManager.INDEX_INDEX_FILE.equalsIgnoreCase(name))
        delete = true;
      else if (BITMAP_INDEX_FILE.matcher(name).matches())
        delete = true;

      if (delete) {
        try {
          FileUtil.deleteFile(file, false);
        } catch (InterruptedException e) {
          interrupted = e;
          // kludge ignore interrupted exception
        }
      }
    }
    if (interrupted != null)
      throw new RuntimeInterruptedException(interrupted);
  }

  public static void checkDropIndexesSetting(File databaseDir) {
    if (Setup.getStringProperty("drop.index") != null) {
      dropIndexes(databaseDir, true);
    }
  }
}
