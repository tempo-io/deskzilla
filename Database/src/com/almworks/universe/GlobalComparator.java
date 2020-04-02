package com.almworks.universe;

import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Particle;
import com.almworks.util.collections.Containers;

import java.util.Comparator;

/**
 * :todoc:
 *
 * @author sereda
 */
class GlobalComparator implements Comparator {
  public int compare(Object o1, Object o2) {
    if (o1 == o2)
      return 0;
    if (o1 instanceof Atom)
      return compareAtomToObject((Atom) o1, o2);
    if (o2 instanceof Atom)
      return -compareAtomToObject((Atom) o2, o1);
    throw new IllegalArgumentException("cannot compare " + o1 + " and " + o2);
  }

  private int compareAtomToObject(Atom atom, Object sample) {
    if (sample == null)
      throw new NullPointerException();
    if (sample instanceof Atom)
      return compareAtomToUCN(atom, ((Atom) sample).getUCN());
    if (sample instanceof Particle.PLong)
      return compareAtomToUCN(atom, ((Particle.PLong) sample).getValue());
    if (sample instanceof Number)
      return compareAtomToUCN(atom, ((Number) sample).longValue());
    throw new IllegalArgumentException("cannot compare " + atom + " to " + sample);
  }

  private int compareAtomToUCN(Atom atom, long sampleUCN) {
    return Containers.compareLongs(sampleUCN, atom.getUCN());
  }
}
