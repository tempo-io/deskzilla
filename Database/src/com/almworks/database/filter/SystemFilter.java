package com.almworks.database.filter;

import com.almworks.api.database.Filter;
import com.almworks.database.Basis;

public abstract class SystemFilter implements Filter {
  protected final Basis myBasis;

  protected SystemFilter(Basis basis) {
    assert basis != null;
    myBasis = basis;
  }

  public abstract boolean isComposite();

  public abstract FilterType getType();

  public abstract Filter[] getChildren();

  public abstract String getPersistableKey();

  /**
   * If true, allows index to be built
   */
  public abstract boolean isIndexable();

  public static boolean isBitmapIndexable(Filter filter) {
    if (filter == ALL || filter == NONE)
      return true;
    if (!(filter instanceof SystemFilter))
      return false;
    SystemFilter systemFilter = (SystemFilter) filter;
    if (!systemFilter.isIndexable())
      return false;
    if (systemFilter.isComposite()) {
      Filter[] children = systemFilter.getChildren();
      for (Filter child : children) {
        if (!isBitmapIndexable(child))
          return false;
      }
    }
    return true;
  }
}
