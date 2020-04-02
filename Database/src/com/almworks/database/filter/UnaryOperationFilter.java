package com.almworks.database.filter;

import com.almworks.api.database.Filter;
import com.almworks.database.Basis;

abstract class UnaryOperationFilter extends CompositeFilter {
  protected final Filter myOperand;

  protected UnaryOperationFilter(Basis basis, Filter operand) {
    super(basis);
    myOperand = operand;
  }

  protected Filter[] createChildrenArray() {
    return myOperand == null ? EMPTY_ARRAY : new Filter[] {myOperand};
  }

  public Filter getOperand() {
    return myOperand;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    UnaryOperationFilter that = (UnaryOperationFilter) o;

    if (myOperand != null ? !myOperand.equals(that.myOperand) : that.myOperand != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myOperand != null ? myOperand.hashCode() : 0;
  }
}
