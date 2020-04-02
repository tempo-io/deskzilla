package com.almworks.database;

import com.almworks.api.database.Artifact;
import com.almworks.api.database.RevisionAccess;

/**
 * :todoc:
 *
 * @author sereda
 */
public class SingleChainRevisionChainTest extends AbstractRevisionChainFixture {

  protected Artifact createArtifact() {
    return createObjectAndSetValue(myAttributeOne, VALUE);
  }

  protected RevisionAccess getAccess() {
    return RevisionAccess.ACCESS_DEFAULT;
  }
}
