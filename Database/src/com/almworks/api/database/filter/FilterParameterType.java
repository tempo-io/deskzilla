package com.almworks.api.database.filter;

import com.almworks.api.database.Value;
import com.almworks.api.database.typed.Attribute;

/**
 * :todoc:
 *
 * @author sereda
 */
public final class FilterParameterType {
  public static final FilterParameterType ATTRIBUTE = new FilterParameterType("ATTRIBUTE", Attribute.class);
  public static final FilterParameterType VARIANT = new FilterParameterType("VARIANT", Value.class);

  public static final FilterParameterType TEXT = new FilterParameterType("TEXT", String.class);
  public static final FilterParameterType WORDS = new FilterParameterType("WORDS", String.class);
  public static final FilterParameterType REGEXP = new FilterParameterType("REGEXP", String.class);

  public static final FilterParameterType INTEGER = new FilterParameterType("INTEGER", Integer.class);

  public static final FilterParameterType ENUM_VALUE_SINGLE = new FilterParameterType("ENUM_VALUE_SINGLE",
    Object.class); // :todo:
  public static final FilterParameterType ENUM_VALUE_MULTIPLE = new FilterParameterType("ENUM_VALUE_MULTIPLE",
    Object[].class); // :todo:

  public static final FilterParameterType DATE = new FilterParameterType("DATE", java.util.Date.class); // :todo: support "now" ?

  private final String myName;
  private final Class myArgumentClass;

  private FilterParameterType(String name, Class argumentClass) {
    if (name == null)
      throw new IllegalArgumentException("name = null");
    myName = name;
    if (argumentClass == null)
      throw new IllegalArgumentException("argumentClass = null");
    myArgumentClass = argumentClass;
  }

  public String getName() {
    return myName;
  }

  public Class getArgumentClass() {
    return myArgumentClass;
  }
}
