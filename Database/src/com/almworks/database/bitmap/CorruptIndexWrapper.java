package com.almworks.database.bitmap;

import com.almworks.database.*;

class CorruptIndexWrapper implements ConsistencyWrapper {
  public static final CorruptIndexWrapper INSTANCE = new CorruptIndexWrapper();

  public <T> T run(Inconsistent<T> inconsistent) {
    try {
      return inconsistent.run();
    } catch (DatabaseInconsistentException e) {
      handle(e, -1);
      return null;
    }
  }

  public void handle(DatabaseInconsistentException exception, int attempt) {
    throw new CorruptIndexException(exception);
  }
}
