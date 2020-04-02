package com.almworks.api.database.util;

import com.almworks.api.database.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class RangeFilter implements Filter {
  private final WCN.Range myRange;

  public RangeFilter(WCN.Range range) {
    assert range != null;
    myRange = range;
  }

  public boolean accept(Revision revision) {
    return myRange.contains(revision.getWCN());
  }

  public String toString() {
    return "wcn in " + myRange;
  }

  public int hashCode() {
    return getClass().hashCode() * 23 + myRange.hashCode();
  }

  public boolean equals(Object o) {
    if (!(o instanceof RangeFilter))
      return false;
    return myRange.equals(((RangeFilter) o).myRange);
  }
}
