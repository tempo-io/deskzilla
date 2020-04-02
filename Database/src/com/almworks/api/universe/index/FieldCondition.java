package com.almworks.api.universe.index;

import com.almworks.api.universe.Atom;
import com.almworks.util.NamedLong;
import com.almworks.util.commons.Condition;

/**
 * :todoc:
 *
 * @author sereda
 */
public class FieldCondition extends Condition<Atom> {
  private final long myKey;

  public FieldCondition(long key) {
    myKey = key;
  }

  public boolean isAccepted(Atom atom) {
    return atom.get(myKey) != null;
  }

  public static Condition<Atom> create(NamedLong key) {
    return create(key.getLong());
  }

  public static Condition<Atom> create(long key) {
    return new FieldCondition(key);
  }
}
