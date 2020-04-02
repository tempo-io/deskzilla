package com.almworks.api.universe.index;

import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Particle;
import com.almworks.util.commons.Condition;

/**
 * :todoc:
 *
 * @author sereda
 */
public class FieldEqualsCondition extends Condition<Atom> {
  private final long myKey;
  private final Particle myValue;

  public FieldEqualsCondition(long key, Object value) {
    myKey = key;
    myValue = Particle.create(value);
  }

  public boolean isAccepted(Atom atom) {
    Particle particle = atom.get(myKey);
    return particle != null && particle.equals(myValue);
  }

  public static Condition<Atom> create(long key, Object value) {
    return new FieldEqualsCondition(key, value);
  }
}
