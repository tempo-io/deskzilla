package com.almworks.universe;

import com.almworks.api.universe.*;
import com.almworks.universe.optimize.UniverseMemoryOptimizer;
import com.almworks.util.tests.BaseTestCase;

public abstract class UniverseFixture extends BaseTestCase {
  protected Universe myUniverse;

  protected UniverseFixture() {
  }

  protected UniverseFixture(int timeout) {
    super(timeout);
  }

  protected void setUp() throws Exception {
    myUniverse = new MemUniverse();
    UniverseMemoryOptimizer.staticCleanup();
  }

  protected void tearDown() throws Exception {
    myUniverse = null;
    UniverseMemoryOptimizer.staticCleanup();
  }


  protected Atom createAtom(long field, Object value) {
    Expansion expansion = myUniverse.begin();
    Atom atom = expansion.createAtom();
    atom.buildJunction(field, Particle.create(value));
    expansion.commit();
    return atom;
  }

  protected Atom createAtom(long field, Object value, long field2, Object value2) {
    Expansion expansion = myUniverse.begin();
    Atom atom = expansion.createAtom();
    atom.buildJunction(field, Particle.create(value));
    atom.buildJunction(field2, Particle.create(value2));
    expansion.commit();
    return atom;
  }

  protected Atom createAtom(long field, Object value, long field2, Object value2, long field3, Object value3) {
    Expansion expansion = myUniverse.begin();
    Atom atom = expansion.createAtom();
    atom.buildJunction(field, Particle.create(value));
    atom.buildJunction(field2, Particle.create(value2));
    atom.buildJunction(field3, Particle.create(value3));
    expansion.commit();
    return atom;
  }

  protected Atom createAtom(Expansion expansion, long key, Object value) {
    Atom atom = expansion.createAtom();
    atom.buildJunction(key, Particle.create(value));
    return atom;
  }
}
