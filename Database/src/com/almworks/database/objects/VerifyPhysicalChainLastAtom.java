package com.almworks.database.objects;

import com.almworks.api.universe.*;
import com.almworks.database.Basis;
import com.almworks.database.schema.Schema;

/**
 * :todoc:
 *
 * @author sereda
 */
public class VerifyPhysicalChainLastAtom implements Verifier {
  private final Basis myBasis;
  private final Atom myAtom;

  public VerifyPhysicalChainLastAtom(Basis basis, Atom atom) {
    assert basis != null;
    assert atom != null;  
    myBasis = basis;
    myAtom = atom;
  }

  public void verify() throws ExpansionVerificationException {
    long prev = myAtom.getLong(Schema.KA_PREV_ATOM);
    if (prev < 0)
      return;
    Index index = myBasis.getIndex(Schema.INDEX_KA_PREV_ATOM);
    Atom atom = index.searchExact(new Long(prev));
    if (atom != null)
      throw new ExpansionVerificationException("atom ref conflict [A:" + prev + "] <= [" + atom + "][" + myAtom + "]");
  }
}
