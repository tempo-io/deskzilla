package com.almworks.bugzilla.provider.datalink;

import com.almworks.util.L;


/**
 * Upload is not possible when there are changes known to be unacceptable by Bugzilla.
 *
 * @author sereda
 */
public class UploadNotPossibleException extends Exception {
  private final String myCause;

  public UploadNotPossibleException(String cause) {
    super(L.content("Upload is not possible because of " + cause));
    myCause = cause;
  }

  public String getCauseString() {
    return myCause;
  }
}
