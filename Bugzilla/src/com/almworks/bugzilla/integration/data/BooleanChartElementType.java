package com.almworks.bugzilla.integration.data;

import com.almworks.util.Enumerable;

/**
 * @author Vasya
 */
public class BooleanChartElementType extends Enumerable<BooleanChartElementType> {
  public static final BooleanChartElementType CONTAINS_ALL = new BooleanChartElementType("allwordssubstr");
  public static final BooleanChartElementType CONTAINS_ANY = new BooleanChartElementType("anywordssubstr");
  public static final BooleanChartElementType DOES_NOT_CONTAIN_REGEXP = new BooleanChartElementType("notregexp");
  public static final BooleanChartElementType EQUALS = new BooleanChartElementType("equals");
  public static final BooleanChartElementType GREATER = new BooleanChartElementType("greaterthan");
  public static final BooleanChartElementType LESS = new BooleanChartElementType("lessthan");
  public static final BooleanChartElementType ANY_WORDS = new BooleanChartElementType("anywords");
  public static final BooleanChartElementType NONE_OF_WORDS = new BooleanChartElementType("nowords");
  public static final BooleanChartElementType NOT_EQUALS = new BooleanChartElementType("notequals");
  public static final BooleanChartElementType CONTAINS_REGEXP = new BooleanChartElementType("regexp");

  private BooleanChartElementType(String name) {
    super(name);
  }
}
