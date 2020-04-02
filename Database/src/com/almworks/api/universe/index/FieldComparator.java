package com.almworks.api.universe.index;


import com.almworks.api.universe.*;
import com.almworks.util.NamedLong;
import com.almworks.util.collections.CollectionUtil;
import com.almworks.util.collections.Containers;

import java.util.Comparator;

/**
 * :todoc:
 *
 * @author sereda
 */
public abstract class FieldComparator implements Comparator {
  private final long myKey;

  public FieldComparator(long key) {
    myKey = key;
  }

  protected abstract int compare(Particle particle1, Particle particle2);

  public int compare(Object o1, Object o2) {
    if (o1 == o2)
      return 0;
    return compare(getValue(o1), getValue(o2));
  }

  private Particle getValue(Object object) {
    if (object == null)
      return null;
    if (object instanceof Particle)
      return (Particle) object;
    if (object instanceof Atom) {
      Atom atom = (Atom) object;
      Particle particle = atom.get(myKey);
      if (particle == null)
        throw new NullPointerException();
      return particle;
    }
    throw new IllegalArgumentException("cannot compare object [" + object + "] of class " + object.getClass());
  }

  private static int defaultCompare(Particle p1, Particle p2) {
    return CollectionUtil.compareArrays(p1.raw(), p2.raw());
  }

  public static FieldComparator create(NamedLong key) {
    return create(key.getLong());
  }

  public static FieldComparator createLong(LongJunctionKey key) {
    return new FieldComparator(key.getLong()) {
      protected int compare(Particle particle1, Particle particle2) {
        if (!(particle1 instanceof Particle.PLong) || !(particle2 instanceof Particle.PLong)) {
          return defaultCompare(particle1, particle2);
        }
        long v1 = ((Particle.PLong) particle1).getValue();
        long v2 = ((Particle.PLong) particle2).getValue();
        return Containers.compareLongs(v1, v2);
      }
    };
  }

  public static FieldComparator create(long key) {
    return new FieldComparator(key) {
      protected int compare(Particle particle1, Particle particle2) {
        return defaultCompare(particle1, particle2);
      }
    };
  }
}
