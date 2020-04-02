package com.almworks.database.bitmap;

import com.almworks.database.DatabaseInconsistentException;

public class CorruptIndexException extends RuntimeException {
  public CorruptIndexException(DatabaseInconsistentException e) {
    super(e);
  }
}
