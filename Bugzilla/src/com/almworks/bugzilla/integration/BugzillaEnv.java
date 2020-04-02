package com.almworks.bugzilla.integration;

import com.almworks.api.connector.http.HttpDumper;
import com.almworks.api.misc.WorkArea;
import com.almworks.platform.DiagnosticRecorder;
import com.almworks.util.Env;
import com.almworks.util.files.FileUtil;
import com.almworks.util.io.IOUtils;
import org.almworks.util.Log;

import java.io.*;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.Properties;

public class BugzillaEnv {
  public static final String ENV_BUGZILLA_DUMP = "bugzilla.dump";
  public static final String DUMP_ERRORS = "errors";
  public static final String DUMP_ALL = "all";
  public static final String BUGZILLA_LOG_SUBDIR = "bugzilla";
  public static final String IGNORE_PRODUCT_DEFAULTS = "dz.ignore.product.defaults";

  // Custom mime-types configuration keys
  public static final String MIME_TYPE_FILE = "mime-types.properties";

  public static volatile File ourLogDir;
  public static volatile DiagnosticRecorder ourRecorder;

  public static HttpDumper.DumpLevel getDumpLevel() {
    String env = Env.getString(ENV_BUGZILLA_DUMP);
    if (DUMP_ALL.equalsIgnoreCase(env))
      return HttpDumper.DumpLevel.ALL;
    else if (DUMP_ERRORS.equalsIgnoreCase(env))
      return HttpDumper.DumpLevel.ERRORS;
    else
      return HttpDumper.DumpLevel.NONE;
  }

  public static File getLogDir() {
    File dir = ourLogDir;
    assert dir != null;
    return dir;
  }

  public static DiagnosticRecorder getDiagnosticRecorder() {
    return ourRecorder;
  }

  public static void setupBugzillaEnv(WorkArea workArea) {
    setupDumps(workArea);
    setupCustomMimeTypes(workArea);
  }

  public static void setupRecorder(DiagnosticRecorder recorder) {
    ourRecorder = recorder;
  }

  /**
   * Checking workarea for avaliability of content-types.properties
   * If file avaliable in Workspace etc dir or in installation etc dir
   * set system property which directs JDK classes to parse it instead
   * of bundled.
   *
   * @param workArea WorkArea object used to retrive needed files
   */
  private static void setupCustomMimeTypes(WorkArea workArea) {
    File mimeTypeFile = workArea.getEtcFile(MIME_TYPE_FILE);
    if (mimeTypeFile == null || !mimeTypeFile.exists() || !mimeTypeFile.canRead())
      return;

    FileInputStream inputStream = null;
    final Properties customTypes = new Properties();
    try {
      inputStream = new FileInputStream(mimeTypeFile);
      customTypes.load(inputStream);
    } catch (IOException e) {
      // ignore. customTypes is empty
    } finally {
      IOUtils.closeStreamIgnoreExceptions(inputStream);
    }

    // Replacing mime-type matching class with our one
    // which tries first custom map, if no success tries default one
    URLConnection.setFileNameMap(new FileNameMap() {
      FileNameMap jreMap = URLConnection.getFileNameMap();

      public String getContentTypeFor(String fileName) {
        String result = customTypes.getProperty(FileUtil.getExtensionLowercase(fileName));
        return result != null ? result : jreMap.getContentTypeFor(fileName);
      }
    });
  }

  private static void setupDumps(WorkArea workArea) {
    String env = Env.getString(ENV_BUGZILLA_DUMP);
    if (env != null)
      Log.debug(ENV_BUGZILLA_DUMP + " = " + env);
    assert ourLogDir == null;
    assert workArea != null;
    File logDir = workArea.getLogDir();
    ourLogDir = new File(logDir, BUGZILLA_LOG_SUBDIR);
  }

  public static void cleanUpForTestCase() {
    ourLogDir = null;
  }
}
