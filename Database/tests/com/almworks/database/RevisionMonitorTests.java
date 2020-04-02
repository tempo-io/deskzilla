package com.almworks.database;

import com.almworks.api.database.Revision;
import com.almworks.api.universe.*;
import com.almworks.database.schema.Schema;
import com.almworks.util.model.ScalarModel;

/**
 * :todoc:
 *
 * @author sereda
 */
public class RevisionMonitorTests extends WorkspaceFixture {
  public void testMonitorEtherealAtom() {
    Expansion expansion = myBasis.ourUniverse.begin();
    Atom atom = expansion.createAtom();
    long chainKey = atom.getAtomID();
    ScalarModel<Revision> model = myBasis.ourRevisionMonitor.getLastRevisionModel(chainKey);
    assertNull(model.getValue());
    atom.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_CHAIN_HEAD));
    atom.buildJunction(Schema.KA_CHAIN_HEAD, Particle.createLong(atom.getAtomID()));
    expansion.commit();
    myBasis.ourRevisionMonitor.updateLastRevisionModel(chainKey);
    assertEquals(chainKey, model.getValue().getKey());
  }
}
