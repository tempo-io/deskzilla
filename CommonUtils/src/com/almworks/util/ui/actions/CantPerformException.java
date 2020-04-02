package com.almworks.util.ui.actions;

import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.List;

/**
 * @author : Dyoma
 */
public class CantPerformException extends Exception {
  public CantPerformException(String message) {
    super(message);
  }

  public CantPerformException() {
  }

  public CantPerformException(Throwable cause) {
    super(cause);
  }

  public CantPerformException(String message, Throwable cause) {
    super(message, cause);
  }

  public static CantPerformException wrongClass(Object object, Class<?> expectedClass) {
    StringBuffer buffer = new StringBuffer();
    buffer.append("Expected instance of ")
      .append(expectedClass.getName())
      .append(" but was: <");
    if (object != null) {
      buffer.append(object.toString())
        .append(" (")
        .append(object.getClass().getName())
        .append(")");
    } else
      buffer.append("null");
    buffer.append(">");
    return new CantPerformException(buffer.toString());
  }

  @NotNull
  public static <T> T cast(Class<? extends T> aClass, @Nullable Object obj) throws CantPerformException {
    if (obj == null || aClass == null)
      throw new CantPerformException("obj=" + obj + " aClass=" + aClass);
    if (aClass.isInstance(obj))
      return (T) obj;
    throw wrongClass(obj, aClass);
  }

  @NotNull
  public static <T> T ensureSingleElement(@NotNull List<? extends T> elements) throws CantPerformException {
    if (elements.size() != 1)
      throw new CantPerformException("Wrong size: " + elements.size());
    T element = elements.get(0);
    assert element != null;
    return element;
  }

  @NotNull
  public static <T> T ensureNotNull(@Nullable T value) throws CantPerformException {
    if (value == null)
      throw new CantPerformException("Expected not null");
    return value;
  }

  public static <C extends Collection<?>> C ensureNotEmpty(@Nullable C collection) throws CantPerformException {
    if (collection == null || collection.isEmpty())
      throw new CantPerformException(String.valueOf(collection));
    return collection;
  }

  public static void ensure(boolean b) throws CantPerformException {
    if(!b) {
      throw new CantPerformException();
    }
  }
}
