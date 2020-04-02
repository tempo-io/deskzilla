package com.almworks.database.objects;

import com.almworks.api.database.RevisionAccess;
import com.almworks.api.database.RevisionChain;
import com.almworks.api.universe.Atom;
import com.almworks.database.Basis;
import com.almworks.database.schema.Schema;

public abstract class RevisionIterator {
  public abstract RevisionWithInternals getPreviousRevision(Basis basis, Atom atom);

  public abstract RevisionAccess getStrategy();

  public abstract RevisionChain getChain(Basis basis, long atomID);

  protected RevisionWithInternals getPhysicallyPreviousRevision(Basis basis, Atom atom) {
    long baseRevisionRef = atom.getLong(Schema.KA_PREV_ATOM);
    if (baseRevisionRef < 0 || baseRevisionRef == atom.getAtomID()) {
      // no prev revision - pointing to ourself or null
      return null;
    } else {
      return basis.getRevision(baseRevisionRef, this);
    }
  }
}
