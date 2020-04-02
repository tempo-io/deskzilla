package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Particle;
import com.almworks.database.*;
import com.almworks.database.schema.Schema;
import com.almworks.util.NamedLong;

/**
 * :todoc:
 *
 * @author sereda
 */
public class CommonArtifactLogic {
  public static RevisionCreator createSingleChain(Basis basis, TransactionExt transaction, NamedLong atomMarker) {

    RevisionCreationContextBean bean = new RevisionCreationContextBean();

    Atom artifactAtom = transaction.createAtom(true);
    long key = artifactAtom.getAtomID();

    NewPhysicalRevisionChain chain = new NewPhysicalRevisionChain(basis, key, bean);
    bean.setRevisionChain(chain);

    LocalArtifact artifact = new LocalArtifact(basis, key, chain);
    //SingularLoader<AtomKey, AbstractArtifactImpl> loader = SingularLoader.create(key, (AbstractArtifactImpl) artifact);
    basis.storeEtherealArtifact(artifact, transaction.getEventSource());
    bean.setArtifact(/*new ArtifactProxy(loader, key, basis)*/basis.getArtifact(key));

    NamedLong marker = atomMarker;
    Particle chainMarker = Particle.createLong(marker);
    artifactAtom.buildJunction(Schema.KL_ATOM_MARKER, chainMarker);

    artifactAtom.buildJunction(Schema.KA_CHAIN_HEAD, Particle.createLong(artifactAtom.getAtomID()));

    RevisionCreatorFacade creator = new RevisionCreatorFacade(basis, artifactAtom, null, bean,
      transaction.getEventSource());
    bean.setRevisionCreator(creator);

    return creator;
  }


  public static RevisionCreator changeSingleChain(Basis basis, TransactionExt transaction, Artifact artifact,
    RevisionAccess strategy, Revision baseRevision) throws DatabaseInconsistentException
  {

    RevisionCreationContextBean bean = new RevisionCreationContextBean();
    bean.setArtifact(artifact);

    Revision lastRevision = artifact.getLastRevision(strategy);
    if (baseRevision != null && !lastRevision.equals(baseRevision))
      throw new IllegalArgumentException("base revision is set to a non-last revision in a chain");

    bean.setRevisionChain(lastRevision.getChain());

    Atom revisionAtom = transaction.createAtom(false);

    long lastRevisionAtomID = lastRevision.getKey();
    long physicalChainAtomID = DBUtil.getPhysicalChainUnsafe(basis, lastRevisionAtomID).getKey();

    revisionAtom.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_REVISION));
    revisionAtom.buildJunction(Schema.KA_CHAIN_HEAD, Particle.createLong(physicalChainAtomID));
    revisionAtom.buildJunction(Schema.KA_PREV_ATOM, Particle.createLong(lastRevisionAtomID));

    RevisionCreator creator = new RevisionCreatorFacade(basis, revisionAtom, lastRevision, bean,
      transaction.getEventSource());
    bean.setRevisionCreator(creator);

    return creator;
  }
}
