package com.almworks.api.database.util;

/**
 * :todoc:
 *
 * @author sereda
 */
public class SingletonNotFoundException extends Exception {
  public SingletonNotFoundException(Singleton singleton) {
    super("singleton " + singleton + " is not found");
  }
}
