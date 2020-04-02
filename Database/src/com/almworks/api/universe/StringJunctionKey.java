package com.almworks.api.universe;

import com.almworks.util.NamedConstantRegistry;

/**
 * :todoc:
 *
 * @author sereda
 */
public final class StringJunctionKey extends JunctionKey {
  public StringJunctionKey(long value, String name) {
    super(value, name);
  }

  public StringJunctionKey(long value, String name, NamedConstantRegistry registry) {
    super(value, name, registry);
  }

  public String get(Atom atom) {
    return atom.getString(getLong());
  }
}
