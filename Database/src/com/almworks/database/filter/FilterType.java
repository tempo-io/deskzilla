package com.almworks.database.filter;

import com.almworks.util.Enumerable;

public class FilterType extends Enumerable {
  public static final FilterType LEAF = new FilterType("LEAF");
  public static final FilterType AND = new FilterType("AND");
  public static final FilterType OR = new FilterType("OR");
  public static final FilterType EXCLUDE = new FilterType("EXCLUDE");

  public FilterType(String name) {
    super(name);
  }
}
