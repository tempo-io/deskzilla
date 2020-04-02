package com.almworks.universe.data;

import com.almworks.api.universe.Atom;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ExpansionInfo {
  public final long UCN;
  public final Atom[] atoms;

  public ExpansionInfo(long UCN, Atom[] atoms) {
    this.UCN = UCN;
    this.atoms = atoms;
  }
}
