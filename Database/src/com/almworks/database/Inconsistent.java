package com.almworks.database;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface Inconsistent <T> {
  T run() throws DatabaseInconsistentException;
}
