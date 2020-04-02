package com.almworks.database.objects.remote;

import com.almworks.api.database.*;
import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Particle;
import com.almworks.database.*;
import com.almworks.database.objects.*;
import com.almworks.database.schema.Schema;

/**
 * This class accumulates logic pertaining to RCB Artifacts.
 *
 * @author sereda
 */
class RCBArtifactLogic {

  /**
   * Creates new artifact. The returned creator could be used to build first revision in the main chain.
   */
  static RevisionCreator createNew(Basis basis, TransactionExt transaction) {
    RevisionCreationContextBean bean = new RevisionCreationContextBean();

    Atom artifactAtom = transaction.createAtom(true);
    Atom binderAtom = transaction.createAtom(true);
    Atom mainchainAtom = transaction.createAtom(true);

    artifactAtom.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_RCB_ARTIFACT));

    mainchainAtom.buildJunction(Schema.KA_CHAIN_ARTIFACT, Particle.createLong(artifactAtom.getAtomID()));
    mainchainAtom.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_CHAIN_HEAD));
    mainchainAtom.buildJunction(Schema.KA_CHAIN_HEAD, Particle.createLong(mainchainAtom.getAtomID()));

    binderAtom.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_RCB_BINDER));
    binderAtom.buildJunction(Schema.KA_CHAIN_HEAD, Particle.createLong(artifactAtom.getAtomID()));
    binderAtom.buildJunction(Schema.KL_RCB_BINDER_REFTYPE,
      Particle.createLong(Schema.RCB_BINDER_REFTYPE_MAINCHAIN));
    binderAtom.buildJunction(Schema.KA_RCB_BINDER_CHAINSTART, Particle.createLong(mainchainAtom.getAtomID()));

    NewPhysicalRevisionChain mainChain = new NewPhysicalRevisionChain(basis, mainchainAtom.getAtomID(),
      bean);
    bean.setRevisionChain(mainChain);

    long key = artifactAtom.getAtomID();
    AbstractArtifactImpl artifact = new RCBArtifactImpl(basis, key);
    basis.storeEtherealArtifact(artifact, transaction.getEventSource());
    bean.setArtifact(basis.getArtifact(key));
    RevisionCreatorFacade creator = new RevisionCreatorFacade(basis, mainchainAtom, null, bean, transaction.getEventSource());
    bean.setRevisionCreator(creator);

    return creator;
  }

  /**
   * Changes main chain - just adds a revision
   */
  static RevisionCreator changeMainChain(Basis basis, TransactionExt transaction, Artifact artifact,
    Revision baseRevision) throws DatabaseInconsistentException
  {
    return CommonArtifactLogic.changeSingleChain(basis, transaction, artifact, RevisionAccess.ACCESS_MAINCHAIN, baseRevision);
  }
}
