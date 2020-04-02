package com.almworks.util.io.persist;

import util.external.CompactInt;

import java.io.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class PersistableBoolean extends LeafPersistable<Boolean> {
  protected Boolean myValue;

  public PersistableBoolean(boolean value) {
    set(Boolean.valueOf(value));
  }

  public PersistableBoolean() {
  }

  protected Boolean doAccess() {
    return myValue;
  }

  protected Boolean doCopy() {
    return myValue;
  }

  protected void doSet(Boolean value) {
    myValue = value;
  }

  protected void doClear() {
    myValue = null;
  }

  protected void doRestore(DataInput in) throws IOException {
    set(Boolean.valueOf(CompactInt.readInt(in) != 0));
  }

  protected void doStore(DataOutput out) throws IOException {
    CompactInt.writeLong(out, myValue.booleanValue() ? 1 : 0);
  }
}
