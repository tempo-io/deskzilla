package com.almworks.database.objects;

import com.almworks.api.database.Revision;
import com.almworks.api.universe.Atom;
import com.almworks.database.*;
import com.almworks.database.schema.Schema;
import com.almworks.util.commons.Lazy;

/**
 * :todoc:
 *
 * @author sereda
 */
public class PhysicalRevisionChain extends AbstractRevisionChain {
  private final Lazy<Revision> myFirst = new Lazy<Revision>() {
    public Revision instantiate() {
      return myBasis.getRevision(myKey, PhysicalRevisionIterator.INSTANCE);
    }
  };

  public PhysicalRevisionChain(Basis basis, long atomKey) {
    super(basis, atomKey);
  }

  public RevisionIterator getRevisionIterator() {
    return PhysicalRevisionIterator.INSTANCE;
  }

  public Revision getFirstRevision() {
    return myFirst.get();
  }

  protected Revision doGetLastRevision() {
    Revision revision = myBasis.ourRevisionMonitor.tryGetLastRevision(myKey);
    if (revision == null) {
      // no model
      ConsistencyWrapper wrapper = myBasis.ourConsistencyWrapper;
      while (true) {
        try {
          Atom atom = DBUtil.getLastAtomInPhysicalChain(myBasis, myKey);
          if (atom == null) {
            throw new DatabaseInconsistentException("cannot get last atom from physical chain " + myKey);
          } else {
            revision = myBasis.getRevision(atom.getAtomID(), getRevisionIterator());
            break;
          }
        } catch (DatabaseInconsistentException e) {
          wrapper.handle(e, -1);
        }
      }
    }
    assert revision != null : this;
    return revision;
  }

  public boolean containsRevision(Revision revision) {
    if (revision == null)
      return false;
    Atom atom = DBUtil.getInternals(revision).getAtom();
    long chainHeadID = Schema.KA_CHAIN_HEAD.get(atom);
    return chainHeadID == myKey;
  }
}

