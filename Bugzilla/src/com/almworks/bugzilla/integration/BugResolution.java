package com.almworks.bugzilla.integration;

import com.almworks.util.Enumerable;
import org.almworks.util.Util;

public final class BugResolution extends Enumerable<BugResolution> {
  public static final BugResolution EMPTY = new BugResolution("");
  public static final BugResolution FIXED = new BugResolution("FIXED");
  public static final BugResolution INVALID = new BugResolution("INVALID");
  public static final BugResolution WONTFIX = new BugResolution("WONTFIX");
  public static final BugResolution LATER = new BugResolution("LATER");
  public static final BugResolution REMIND = new BugResolution("REMIND");
  public static final BugResolution DUPLICATE = new BugResolution("DUPLICATE");
  public static final BugResolution WORKSFORME = new BugResolution("WORKSFORME");
  public static final BugResolution MOVED = new BugResolution("MOVED");

  private BugResolution(String name) {
    super(name);
  }

  public static BugResolution forName(String name) {
    if (name == null)
      return null;
    return forName(BugResolution.class, Util.upper(name).trim());
  }

  public static int count() {
    return Enumerable.count(BugResolution.class);
  }
}
