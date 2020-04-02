package com.almworks.bugzilla.integration;

import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BugzillaVersion implements Comparable<BugzillaVersion> {
  private final int myMaj;
  private final int myMin;
  private final int myPatch;
  @Nullable
  private final String myOriginal;
  
  private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)(?:[.](\\d+))?(?:[.](\\d+))?.*$");
  public static final BugzillaVersion V3_6 = parse("3.6");
  public static final BugzillaVersion V4_0 = parse("4.0");
  public static final BugzillaVersion V4_2 = parse("4.2");
  public static final BugzillaVersion V4_4 = parse("4.4");
  public static final BugzillaVersion V5_0 = parse("5.0");

  /**
   * For test purposes only
   */
  BugzillaVersion(int maj, int min, int patch) {
    this(maj, min, patch, null);
  }
  
  private BugzillaVersion(int maj, int min, int patch, @Nullable String original) {
    myMaj = maj;
    myMin = min;
    myPatch = patch;
    myOriginal = original;
  }
  
  @Nullable
  public static BugzillaVersion parse(@Nullable String version) {
    if (version == null) return null;
    Matcher m = VERSION_PATTERN.matcher(version);
    if (!m.matches()) {
      Log.warn("Unparseable Bugzilla version: " + version);
      return null;
    } 

    int maj = 0;
    int min = 0;
    int patch = 0;
    try {
      maj = Integer.parseInt(m.group(1));
      min = Integer.parseInt(m.group(2));
      try {
        String sPatch = m.group(3);
        if (sPatch != null) patch = Integer.parseInt(sPatch);
      } catch (NumberFormatException ex) {
        Log.debug("Unexpected end of version string: " + version, ex);
      }
      if (maj <= 0) {
        Log.warn("Invalid version string:" + version + " " + maj);
      }
      return new BugzillaVersion(maj, min, patch, version);
    } catch (NumberFormatException ex) {
      Log.warn("Cannot parse version: " + version, ex);
    }
    return null;
  }

  @Override
  public String toString() {
    return myMaj + "." + myMin + '.' + myPatch + " [" + myOriginal + ']';
  }
  
  @Override
  public int compareTo(BugzillaVersion o) {
    // Unknown version? Think the latest one
    if (o == null) return -1; 
    return 
      myMaj < o.myMaj ? -1 : myMaj > o.myMaj ? 1 :
      myMin < o.myMin ? -1 : myMin > o.myMin ? 1 : 
      myPatch < o.myPatch ? -1 : myPatch > o.myPatch ? 1 :
      0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    
    BugzillaVersion that = (BugzillaVersion) o;

    if (myMaj != that.myMaj)
      return false;
    if (myMin != that.myMin)
      return false;
    if (myPatch != that.myPatch)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMaj;
    result = 31 * result + myMin;
    result = 31 * result + myPatch;
    return result;
  }
}
