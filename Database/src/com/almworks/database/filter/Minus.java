package com.almworks.database.filter;

import com.almworks.api.database.Filter;
import com.almworks.database.Basis;

/**
 * :todoc:
 *
 * @author sereda
 */
class Minus extends And {
  public Minus(Basis basis, Filter firstOperand, Filter secondOperand) {
    super(basis, firstOperand, new Not(basis, secondOperand));
  }
}
