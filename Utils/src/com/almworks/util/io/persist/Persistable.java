package com.almworks.util.io.persist;

import java.io.*;
import java.util.List;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface Persistable<V> {
  void store(DataOutput out) throws IOException;

  void restore(DataInput in) throws IOException, FormatException;

  boolean isInitialized();

  List<Persistable> getChildren();

  V access();

  V copy();

  void set(V value);

  void clear();
}
