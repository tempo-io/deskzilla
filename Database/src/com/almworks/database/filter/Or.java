package com.almworks.database.filter;

import com.almworks.api.database.Filter;
import com.almworks.api.database.Revision;
import com.almworks.database.Basis;

/**
 * :todoc:
 *
 * @author sereda
 */
class Or extends BinaryOperationFilter {
  public Or(Basis basis, Filter firstOperand, Filter secondOperand) {
    super(basis, firstOperand, secondOperand);
  }

  public boolean accept(Revision revision) {
    return myFirstOperand.accept(revision) || mySecondOperand.accept(revision);
  }

  public String toString() {
    return "(" + myFirstOperand + ") or (" + mySecondOperand + ")";
  }

  public FilterType getType() {
    return FilterType.OR;
  }
}
