package com.almworks.bugzilla.integration.err;

import com.almworks.util.L;

/**
 * :todoc:
 *
 * @author sereda
 */
public class MidAirCollisionException extends UploadException {
  private final String myTimestamp;

  public MidAirCollisionException(String timestamp) {
    super("mid-air collision", L.tooltip("Mid-air collision detected"),
      L.tooltip("Bugzilla reports mid-air collision, which means" +
      " that someone has made changes to the bug that you were not aware of when doing your own changes. New " +
      "bug timestamp: " + timestamp));
    myTimestamp = timestamp;
  }

  public String getTimestamp() {
    return myTimestamp;
  }
}
