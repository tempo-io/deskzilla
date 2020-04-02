package com.almworks.bugzilla.integration;

public interface QueryURL {
  String getURL() throws IllegalStateException;
  
  interface Changeable extends QueryURL {
    void setOrderBy(Column... columns);

    boolean isLimitSet();

    void setLimit(int limit);

    void setOffset(int offset);
  }


  enum Column {
    MODIFICATION_DATE(false),
    MODIFICATION_DATE_DESC(true),
    ID(false),
    ID_DESC(true);
    
    private final boolean myDesc;

    private Column(boolean desc) {
      myDesc = desc;
    }

    public boolean isDesc() {
      return myDesc;
    }
  }
}
