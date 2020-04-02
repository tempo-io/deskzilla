package com.almworks.database.schema;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.TypedArtifact;
import com.almworks.api.database.util.Initializer;
import com.almworks.api.database.util.SingletonNotFoundException;
import com.almworks.api.universe.*;
import com.almworks.database.Basis;
import com.almworks.database.TransactionExt;
import com.almworks.util.TODO;

/**
 * :todoc:
 *
 * @author sereda
 */
public class SystemSingleton <T extends TypedArtifact> extends com.almworks.api.database.util.Singleton<T> {
  private final Basis myBasis;

  public SystemSingleton(Basis basis, String ID, Initializer initializer) {
    super(ID, initializer);
    myBasis = basis;
  }

  /**
   * This is a special way to initialize creator of system signleton. We have to use addition
   * type of Atom (non-accessible via Workspace), because at the time of these calls ValueTypes
   * are not installed and ordinary Artifacts cannot be created or queried.
   */
  public RevisionCreator createCreator(Transaction transaction, ArtifactView rootView, FilterManager filterManager) {
    Artifact artifact = loadArtifact();
    if (artifact != null) {
      return transaction.changeArtifact(artifact);
    }
    RevisionCreator creator = transaction.createArtifact();
    Atom markerAtom = ((TransactionExt) transaction).createAtom(true); // todo remove cast
    markerAtom.buildJunction(Schema.KL_ATOM_MARKER, Particle.createLong(Schema.ATOM_SYSTEM_SINGLETON_TOKEN));
    markerAtom.buildJunction(Schema.KS_SINGLETON_NAME, Particle.create(myID));
    markerAtom.buildJunction(Schema.KA_SINGLETON_ARTIFACT,
      Particle.createLong(myBasis.getAtomID(creator.asRevision())));
    creator.forceCreation();
    return creator;
  }

  private Artifact loadArtifact() {
    Index index = myBasis.ourUniverse.getIndex(Schema.INDEX_SYSTEM_SINGLETONS);
    if (index == null)
      throw TODO.failure();
    Atom markerAtom = index.searchExact(myID);
    if (markerAtom == null)
      return null;
    long singletonAtomID = markerAtom.getLong(Schema.KA_SINGLETON_ARTIFACT);
    if (singletonAtomID < 0)
      throw TODO.failure();
    Artifact artifact = myBasis.getArtifact(singletonAtomID);
    return artifact;
  }

  public void initialize(RevisionCreator creator) {
    creator.setValue(myBasis.ATTRIBUTE.id, myID);
    creator.setValue(myBasis.ATTRIBUTE.isSystemObject, Boolean.TRUE);
    super.initialize(creator);
  }

  public ArtifactPointer loadExisting(ArtifactView rootView, FilterManager filterManager)
    throws SingletonNotFoundException {
    Artifact artifact = loadArtifact();
    if (artifact == null)
      throw new SingletonNotFoundException(this);
    return artifact;
  }

  public String toString() {
    return "SS::" + myID;
  }
}
