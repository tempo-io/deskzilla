package com.almworks.bugzilla.integration.err;

public class UnsupportedStatusException extends UploadException {
  public UnsupportedStatusException(String status) {
    super(
      status + " status is not supported",
      status + " status is not supported",
      status + " status is not supported.\n\n" +
      "You have set the status of the bug to " + status + ".\n" +
      "Changing bug status to this value is not yet supported.\n" +
      "Please change the status using Bugzilla web interface.");
  }
}
