package com.almworks.bugzilla.integration.err;

import com.almworks.util.L;

/**
 * :todoc:
 *
 * @author sereda
 */
public class BadWorkflowChange extends UploadException {
  public BadWorkflowChange(String message) {
    super(message,
      L.tooltip("Illegal change of workflow fields"),
      L.tooltip("Illegal change of workflow fields.\n\n" +
      message + "\n\n" +
      "It seems that you have tried to change Status, Resolution or Assigned To fields in" +
      " a way that is not supported by Bugzilla. Please revisit your changes and try again."
      ));
  }
}
