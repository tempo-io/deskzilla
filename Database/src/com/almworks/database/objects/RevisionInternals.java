package com.almworks.database.objects;

import com.almworks.api.database.RevisionAccess;
import com.almworks.api.universe.Atom;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface RevisionInternals {
  Atom getAtom();

  RevisionInternals getPrevRevisionInternals();

  void invalidateValuesCache(RevisionAccess access);

  /**
   * Forces creation of this artifact, if it is ethereal.
   * Does nothing on an existing artifact.
   */
  void forceCreation();
}
