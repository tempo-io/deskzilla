package com.almworks.api.universe;

import com.almworks.util.NamedConstantRegistry;

/**
 * :todoc:
 *
 * @author sereda
 */
public final class LongJunctionKey extends JunctionKey {
  public LongJunctionKey(long value, String name, NamedConstantRegistry registry) {
    super(value, name, registry);
  }

  public long get(Atom atom) {
    return atom.getLong(getLong());
  }
}
