package com.almworks.database.objects;

import com.almworks.api.database.RevisionAccess;
import com.almworks.api.database.RevisionChain;
import com.almworks.api.universe.Atom;
import com.almworks.database.Basis;
import com.almworks.database.DatabaseInconsistentException;

/**
 * :todoc:
 *
 * @author sereda
 */
public class PhysicalRevisionIterator extends RevisionIterator {
  public static final PhysicalRevisionIterator INSTANCE = new PhysicalRevisionIterator();

  public RevisionWithInternals getPreviousRevision(Basis basis, Atom atom) {
    return getPhysicallyPreviousRevision(basis, atom);
  }

  public RevisionAccess getStrategy() {
    return RevisionAccess.ACCESS_DEFAULT;
  }

  public RevisionChain getChain(Basis basis, long atomID) {
    while (true) {
      try {
        return DBUtil.getPhysicalChainUnsafe(basis, atomID);
      } catch (DatabaseInconsistentException e) {
        basis.ourConsistencyWrapper.handle(e, -1);
      }
    }
  }

  protected PhysicalRevisionIterator() {
  }
}
