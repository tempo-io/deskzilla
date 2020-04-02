package com.almworks.database;

import com.almworks.api.database.Artifact;

/**
 * :todoc:
 *
 * @author sereda
 */
public abstract class RCBRevisionChainFixture extends AbstractRevisionChainFixture {
  protected Artifact createArtifact() {
    return createRCB(VALUE);
  }
}
