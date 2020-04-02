package com.almworks.database.filter;

import com.almworks.api.database.Filter;
import com.almworks.api.database.Revision;
import com.almworks.database.Basis;

/**
 * :todoc:
 *
 * @author sereda
 */
class And extends BinaryOperationFilter {
  public And(Basis basis, Filter firstOperand, Filter secondOperand) {
    super(basis, firstOperand, secondOperand);
  }

  public boolean accept(Revision revision) {
    return myFirstOperand.accept(revision) && mySecondOperand.accept(revision);
  }

  public String toString() {
    return "(" + myFirstOperand + ") and (" + mySecondOperand + ")";
  }

  public FilterType getType() {
    return FilterType.AND;
  }
}
