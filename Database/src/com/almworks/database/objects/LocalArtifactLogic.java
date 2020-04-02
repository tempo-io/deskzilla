package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.database.*;
import com.almworks.database.schema.Schema;

/**
 * This class contains atom-handling logic for local artifacts (artifacts with one chain).
 *
 * @author sereda
 */
public class LocalArtifactLogic {
  /**
   * Create new artifact: makes single atom - chain head and first revision.
   */
  public static RevisionCreator createNew(Basis basis, TransactionExt transaction) {
    return CommonArtifactLogic.createSingleChain(basis, transaction, Schema.ATOM_LOCAL_ARTIFACT);
  }

  /**
   * Changes existing artifact by making new revision at the end of the chain.
   */
  public static RevisionCreator changeArtifact(Basis basis, TransactionExt transaction, Artifact artifact,
    RevisionAccess accessStrategy, Revision baseRevision) throws DatabaseInconsistentException
  {

    if (accessStrategy != RevisionAccess.ACCESS_DEFAULT)
      throw new UnsupportedOperationException(
        "strategy " + accessStrategy + " is not supported by " + LocalArtifact.class + ": " + artifact);

    return CommonArtifactLogic.changeSingleChain(basis, transaction, artifact, RevisionAccess.ACCESS_DEFAULT, baseRevision);
  }

}
