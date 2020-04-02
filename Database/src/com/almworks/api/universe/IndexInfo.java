package com.almworks.api.universe;

import com.almworks.util.commons.Condition;

import java.util.Comparator;

public class IndexInfo {
  private final Comparator myComparator;
  private final Condition<Atom> myCondition;
  private final int myIndexID;
  private final String myName;

  public IndexInfo(String name, Comparator comparator, Condition<Atom> condition) {
    this(-1, name, comparator, condition);
  }

  public IndexInfo(int indexID, String name, Comparator comparator, Condition<Atom> condition) {
    assert comparator != null;
    assert condition != null;
    assert name != null;
    myIndexID = indexID;
    myName = name;
    myComparator = comparator;
    myCondition = condition;
  }

  public Comparator getComparator() {
    return myComparator;
  }

  public Condition<Atom> getCondition() {
    return myCondition;
  }

  public int getIndexID() {
    return myIndexID;
  }

  public String getName() {
    return myName;
  }
}
