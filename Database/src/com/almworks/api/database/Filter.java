package com.almworks.api.database;

import com.almworks.util.commons.Condition;

public interface Filter {
  Filter ALL = new All();
  Filter NONE = new None();
  Filter[] EMPTY_ARRAY = {};


  boolean accept(Revision revision);

  int hashCode();

  boolean equals(Object obj);


  interface Equals extends Filter {
    ArtifactPointer getAttribute();
    Value getValue();
  }


  class FilterCondition extends Condition<Revision> {
    private final Filter myFilter;

    public FilterCondition(Filter filter) {
      myFilter = filter;
    }

    public boolean isAccepted(Revision value) {
      return myFilter.accept(value);
    }
  }


  public static class All implements Filter {
    public boolean accept(Revision revision) {
      return true;
    }

    public String toString() {
      return "true";
    }

    public int hashCode() {
      return -1;
    }

    public boolean equals(Object obj) {
      return obj == ALL;
    }
  }


  public static class None implements Filter {
    public boolean accept(Revision revision) {
      return false;
    }

    public String toString() {
      return "false";
    }

    public int hashCode() {
      return 1;
    }

    public boolean equals(Object obj) {
      return obj == NONE;
    }
  }
}
