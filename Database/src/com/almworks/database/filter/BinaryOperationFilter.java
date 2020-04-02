package com.almworks.database.filter;

import com.almworks.api.database.Filter;
import com.almworks.database.Basis;
import org.almworks.util.Util;

abstract class BinaryOperationFilter extends CompositeFilter {
  protected final Filter myFirstOperand;
  protected final Filter mySecondOperand;

  public BinaryOperationFilter(Basis basis, Filter firstOperand, Filter secondOperand) {
    super(basis);
    assert firstOperand != null;
    assert secondOperand != null;
    myFirstOperand = firstOperand;
    mySecondOperand = secondOperand;
  }

  public Filter getFirstOperand() {
    return myFirstOperand;
  }

  public Filter getSecondOperand() {
    return mySecondOperand;
  }

  protected Filter[] createChildrenArray() {
    Filter f = myFirstOperand;
    Filter s = mySecondOperand;
    if (f == null) {
      if (s == null) {
        return Filter.EMPTY_ARRAY;
      } else {
        return new Filter[] {s};
      }
    } else {
      if (s == null) {
        return new Filter[] {f};
      } else {
        return new Filter[] {f, s};
      }
    }
  }

  public int hashCode() {
    int r = getClass().hashCode() * 23;
    int f = myFirstOperand == null ? 0 : myFirstOperand.hashCode();
    int s = mySecondOperand == null ? 0 : mySecondOperand.hashCode();
    // order-independent
    r = r * 31 + Math.min(f, s);
    r = r * 31 + Math.max(f, s);
    return r;
  }

  public boolean equals(Object o) {
    if (o == null)
      return false;
    if (!getClass().equals(o.getClass()))
      return false;
    BinaryOperationFilter that = (BinaryOperationFilter) o;
    return (Util.equals(myFirstOperand, that.myFirstOperand) && Util.equals(mySecondOperand, that.mySecondOperand))
      || (Util.equals(myFirstOperand, that.mySecondOperand) && Util.equals(mySecondOperand, that.myFirstOperand));
  }
}
