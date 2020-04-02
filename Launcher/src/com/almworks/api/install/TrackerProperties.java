package com.almworks.api.install;

import java.util.HashSet;
import java.util.Set;

/**
 * This interface lists the deskzilla-specific environmental properties.
 */
public class TrackerProperties {
  private static Set<String> myProperties = new HashSet<String>();

  protected static String register(String property) {
    myProperties.add(property);
    return property;
  }

  public static final String DEBUG = register("deskzilla.debug");
  public static final String DEBUG_LEVEL = register("deskzilla.debug.level");
  public static final String HOME = register("deskzilla.home");
  public static final String LOGCONFIG = register("logging.config");
  public static final String COMPATIBILITY_LEVEL = register("deskzilla.compatibility");
  public static final String FORCE_CHARSET = register("force.charset");
  public static final String DEBUG_COMPONENTS = register("debug.components");
  public static final String COMMIT_LOCK_TIMEOUT = register("commitlock.timeout");

  // since Deskzilla 1.2
  public static final String NO_LOCKING = register("no.locking");
  public static final String IGNORE_LOCKING = register("ignore.locking");
  public static final String WORKSPACE = register("deskzilla.workspace");
  public static final String TRACKER_LICENSE = register("deskzilla.license");

  // since Deskzilla 1.2.1
  public static final String FORM_DROPDOWN_COUNT = register("form.combobox.drop.count");

  // since JIRA Client 0.6 / Deskzilla 1.3
  public static final String AUTO_DETAIL_DELAY = register("auto.detail.delay");
  public static final String AUTO_DOWNLOAD_MAXSIZE = register("auto.download.maxsize");

  // since JIRA Client 1.0 / Deskzilla 1.3
  public static final String DEBUG_LICENSE = register("debug.ls");

  private TrackerProperties() {}

  public static boolean hasProperty(String property) {
    return myProperties.contains(property);
  }
}
