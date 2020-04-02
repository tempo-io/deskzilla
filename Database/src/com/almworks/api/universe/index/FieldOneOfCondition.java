package com.almworks.api.universe.index;

import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Particle;
import com.almworks.util.commons.Condition;

/**
 * :todoc:
 *
 * @author sereda
 */
public class FieldOneOfCondition extends Condition<Atom> {
  private final long myKey;
  private final Particle[] myValues;

  public FieldOneOfCondition(long key, Object[] values) {
    myKey = key;
    myValues = new Particle[values.length];
    for (int i = 0; i < values.length; i++) {
      myValues[i] = Particle.create(values[i]);
    }
  }

  public static Condition<Atom> create(long key, Object[] values) {
    return new FieldOneOfCondition(key, values);
  }

  public boolean isAccepted(Atom atom) {
    Particle particle = atom.get(myKey);
    if (particle == null)
      return false;
    for (int i = 0; i < myValues.length; i++) {
      Particle value = myValues[i];
      if (value.equals(particle))
        return true;
    }
    return false;
  }
}
