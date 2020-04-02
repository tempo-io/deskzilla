package org.almworks.util;

import org.jetbrains.annotations.*;

import java.net.URL;
import java.util.*;

public class Util {
  /**
   * Returns integer value of a string, if it can be parsed. If not, returns default value.
   *
   * @param value a string that probably holds an integer
   * @param defaultValue default value to return if string cannot be parsed
   * @return integer value of the string
   */
  public static int toInt(@Nullable String value, int defaultValue) {
    if (value == null)
      return defaultValue;
    try {
      return Integer.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * Returns long value of a string, if it can be parsed. If not, returns default value.
   *
   * @param value a string that probably holds a long value
   * @param defaultValue default value to return if string cannot be parsed
   * @return integer value of the string
   */
  public static long toLong(@Nullable String value, long defaultValue) {
    if (value == null)
      return defaultValue;
    try {
      return Long.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * Null guardian, returns non-null.
   */
  @NotNull
  public static <T> T NN(@Nullable T nullable, @NotNull T defaultValue) {
    return nullable != null ? nullable : defaultValue;
  }

  /**
   * Default guardian for strings.
   */
  @NotNull
  public static String NN(@Nullable String nullable) {
    return nullable != null ? nullable : "";
  }

  public static <T> T cast(Class<T> aClass, Object object) throws ClassCastException {
    if (object == null)
      return null;
    if (aClass.isInstance(object))
      return (T) object;
    throw new ClassCastException("required: " + aClass + "; received: " + object.getClass() + "; " + object);
  }

  @NotNull
  public static <T> T castNotNull(Class<T> aClass, @NotNull Object object) throws ClassCastException {
    T result = cast(aClass, object);
    assert result != null : object + " " + aClass;
    return result;
  }

  @Nullable
  public static <T> T castNullable(@Nullable Class<T> aClass, @Nullable Object object) {
    if (aClass == null || object == null)
      return null;
    if (object.getClass() == aClass)
      return (T) object;
    return aClass.isInstance(object) ? (T) object : null;
  }

  /**
   * Compares A and B using {@link Object#equals(Object)}. Safely compares nulls. Safely compares URLs.
   *
   * @param a first object
   * @param b second object
   * @return true if a.equals(b) or both arguments are null
   */
  public static boolean equals(@Nullable Object a, @Nullable Object b) {
    if (a == b)
      return true;
    if (a == null || b == null)
      return false;
    boolean aUrl = a instanceof URL;
    boolean bUrl = b instanceof URL;
    if (aUrl || bUrl) {
      // URLs take long time to verify
      return aUrl && bUrl && ((URL) a).toExternalForm().equals(((URL) b).toExternalForm());
    }
    return a.equals(b);
  }

  /**
   * @return true iff both arrays are null, or both contains equal ({@link #equals(Object, Object)}) elements in same order
   */
  public static boolean arraysOrderEqual(@Nullable Object[] array1, @Nullable Object[] array2) {
    if (array1 == array2)
      return true;
    if (array1 == null || array2 == null)
      return false;
    if (array1.length != array2.length)
      return false;
    for (int i = 0; i < array1.length; i++) 
      if (!equals(array1[i], array2[i]))
        return false;
    return true;
  }

  /**
   * @return true iff both collections are null, or contains equal ({@link #equals(Object, Object)} elements in same order
   */
  public static boolean collectionsOrderEqual(@Nullable Collection<?> collection1, @Nullable Collection<?> collection2) {
    if (collection1 == collection2)
      return true;
    if (collection1 == null || collection2 == null)
      return false;
    if (collection1.size() != collection2.size())
      return false;
    Iterator<?> it1 = collection1.iterator();
    Iterator<?> it2 = collection2.iterator();
    while (it1.hasNext() && it2.hasNext())
      if (!equals(it1.next(), it2.next()))
        return false;
    return true;
  }

  /**
   * A shortcut for String.toUpperCase that is not dependent on default locale.
   */
  public static String upper(String string) {
    return string == null ? null : string.toUpperCase(Locale.US);
  }

  /**
   * A shortcut for String.toLowerCase that is not dependent on default locale.
   */
  public static String lower(String string) {
    return string == null ? null : string.toLowerCase(Locale.US);
  }

  public static int NN(Integer value, int nullValue) {
    return value == null ? nullValue : value;
  }

  public static long NN(Long value, long nullValue) {
    return value == null ? nullValue : value;
  }

  public static boolean NN(Boolean value, boolean nullValue) {
    return value == null ? nullValue : value;
  }

  public static int compareInts(int a, int b) {
    return (a < b ? -1 : (a == b ? 0 : 1));
  }

  public static int compareLongs(long a, long b) {
    return (a < b ? -1 : (a == b ? 0 : 1));
  }

  public static int minmax(int value, int min, int max) {
    assert min <= max : min + " " + max;
    return value < min ? min : (value > max ? max : value); 
  }

  @Nullable
  public static String stringOrNull(@Nullable Integer i) {
    return i == null ? null : i.toString();
  }

  public static String getClassPath(Class<?> clazz) {
    String packagePath = clazz.getPackage().getName().replace('.', '/');
    return packagePath.isEmpty() ? "" : packagePath + "/";
  }
}
