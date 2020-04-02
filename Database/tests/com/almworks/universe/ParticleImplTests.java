package com.almworks.universe;

import com.almworks.api.universe.Particle;
import com.almworks.util.tests.BaseTestCase;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ParticleImplTests extends BaseTestCase {
  private State myState = new State();

  Particle create(Object object) {
    return Particle.create(object);
  }

  public void testString() {
    String value1 = "xxxs";
    Particle p1 = create(value1);
    assertTrue(((Particle.PIsoString) p1).getValue().equals(value1));
    Particle p2 = create(value1);
    assertTrue(p1.equals(p2));
    assertTrue(p1.hashCode() == p2.hashCode());
  }

  public void testLong() {
    Long value1 = new Long(239023934343L);
    Particle p1 = create(value1);
    assertTrue(((Particle.PLong) p1).getValue() == value1.longValue());
    Particle p2 = create(value1);
    assertTrue(p1.equals(p2));
    assertTrue(p1.hashCode() == p2.hashCode());
  }
}
