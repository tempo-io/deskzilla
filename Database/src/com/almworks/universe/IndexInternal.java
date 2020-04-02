package com.almworks.universe;

import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Index;
import com.almworks.util.collections.Containers;

import java.util.Comparator;

public abstract class IndexInternal implements Index {
  public abstract StringBuffer writeStats(StringBuffer result);

  public abstract int testAtomInSet(Atom atom);

  protected static Comparator createUcnComparator(final Comparator comparator) {
    return new Comparator() {
      public int compare(Object o1, Object o2) {
        if (o1 == o2 || o1.equals(o2))
          return 0;
        int r = comparator.compare(o1, o2);
        if (r != 0)
          return r;

        boolean o1Atom = o1 instanceof Atom;
        boolean o2atom = o2 instanceof Atom;
        
        long ucn1 = o1Atom ? ((Atom) o1).getUCN() : Long.MAX_VALUE;
        long ucn2 = o2atom ? ((Atom) o2).getUCN() : Long.MAX_VALUE;
        r = Containers.compareLongs(ucn2, ucn1);
        if (r != 0)
          return r;

        long aid1 = o1Atom ? ((Atom) o1).getAtomID() : Long.MAX_VALUE;
        long aid2 = o2atom ? ((Atom) o2).getAtomID() : Long.MAX_VALUE;
        r = Containers.compareLongs(aid2, aid1);

        return r;
      }
    };
  }
}
