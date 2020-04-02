package com.almworks.api.universe;

import java.util.Iterator;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface Index {
  IndexInfo getInfo();

  int getSelectivityHint();

  Atom first();

  Atom last();

  Atom searchExact(Object sample);

  Iterator<Atom> search(Object sample);

  Iterator<Atom> all();

}
