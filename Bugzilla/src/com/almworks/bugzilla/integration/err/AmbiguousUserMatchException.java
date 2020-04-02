package com.almworks.bugzilla.integration.err;

import com.almworks.util.English;

public class AmbiguousUserMatchException extends UploadException {
  public AmbiguousUserMatchException(String message) {
    super(message, English.capitalize(message), English.capitalize(message));
  }
}
