package com.almworks.database.filter;

import com.almworks.api.database.Filter;
import com.almworks.database.Basis;

public abstract class CompositeFilter extends SystemFilter {
  private Filter[] myChildren;

  protected CompositeFilter(Basis basis) {
    super(basis);
  }

  public final boolean isComposite() {
    return true;
  }

  public Filter[] getChildren() {
    if (myChildren != null)
      return myChildren;
    Filter[] children = createChildrenArray();
    myChildren = children;
    return children;
  }

  protected abstract Filter[] createChildrenArray();

  public final String getPersistableKey() {
    return null;
  }

  public boolean isIndexable() {
    return true;
  }
}
