package com.almworks.bugzilla.integration.err;

import com.almworks.util.L;

/**
 * :todoc:
 *
 * @author sereda
 */
public class InconsistentProductChange extends UploadException {
  public InconsistentProductChange() {
    super("Inconsistent Product Change", L.tooltip("Inconsistent Product Change"),
      L.tooltip("You have changed Product but didn't change " +
      "Component, Version or Target Milestone, or changed them incorrectly"));
  }
}
