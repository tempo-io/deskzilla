package com.almworks.database.filter;

import com.almworks.api.database.Filter;
import com.almworks.api.database.Revision;
import com.almworks.database.Basis;

/**
 * :todoc:
 *
 * @author sereda
 */
class Not extends UnaryOperationFilter {
  public Not(Basis basis, Filter operand) {
    super(basis, operand);
  }

  public boolean accept(Revision revision) {
    return !myOperand.accept(revision);
  }

  public String toString() {
    return "not (" + myOperand + ")";
  }

  public FilterType getType() {
    return FilterType.EXCLUDE;
  }
}
