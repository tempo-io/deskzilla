package com.almworks.universe.data;

import com.almworks.api.universe.Atom;
import org.almworks.util.Collections15;

import java.util.Arrays;
import java.util.List;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ExpansionInfoBuilder {
  private long myUCN = -1;
  private final List<Atom> myAtoms = Collections15.arrayList();

  public long getUCN() {
    return myUCN;
  }

  public void setUCN(long UCN) {
    myUCN = UCN;
  }

  public void addAtom(Atom atom) {
    myAtoms.add(atom);
  }

  public void clear() {
    myUCN = -1;
    myAtoms.clear();
  }

  public ExpansionInfo build() {
    if (myUCN < 0)
      throw new IllegalStateException("ucn " + myUCN);
    if (myAtoms.size() == 0)
      throw new IllegalStateException("no atoms");
    Atom[] atoms = myAtoms.toArray(new Atom[myAtoms.size()]);
    for (int i = 0; i < atoms.length; i++) {
      atoms[i].buildFinished(myUCN);
    }
    ExpansionInfo info = new ExpansionInfo(myUCN, atoms);
    clear();
    return info;
  }

  public void copyFrom(ExpansionInfo info) {
    myUCN = info.UCN;
    myAtoms.clear();
    myAtoms.addAll(Arrays.asList(info.atoms));
  }
}
