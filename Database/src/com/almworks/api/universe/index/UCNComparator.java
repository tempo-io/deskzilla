package com.almworks.api.universe.index;

import com.almworks.api.universe.Atom;
import com.almworks.util.collections.Containers;

import java.util.Comparator;

/**
 * :todoc:
 *
 * @author sereda
 */
public class UCNComparator implements Comparator {
  public int compare(Object o1, Object o2) {
    assert o1 != null;
    assert o2 != null;
    return Containers.compareLongs(getUcn(o2), getUcn(o1));
  }

  private long getUcn(Object o) {
    if (o instanceof Atom)
      return ((Atom)o).getUCN();
    if (o instanceof Long)
      return ((Long)o).longValue();
    throw new IllegalArgumentException("cannot understand " + o);
  }
}
