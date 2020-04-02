package com.almworks.api.universe;

import com.almworks.util.NamedConstantRegistry;
import com.almworks.util.NamedLong;

/**
 * :todoc:
 *
 * @author sereda
 */
public abstract class JunctionKey extends NamedLong {
  protected JunctionKey(long value, String name) {
    super(value, name);
  }

  protected JunctionKey(long value, String name, NamedConstantRegistry registry) {
    super(value, name, registry);
  }

  public long getKey() {
    return getLong();
  }
}
